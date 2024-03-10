package me.aap.fermata.media.engine;

import static android.content.Context.MODE_PRIVATE;
import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.io.FileUtils.getFileExtension;
import static me.aap.utils.net.http.HttpFileDownloader.MAX_AGE;
import static me.aap.utils.security.SecurityUtils.SHA1_DIGEST_LEN;
import static me.aap.utils.security.SecurityUtils.sha1;
import static me.aap.utils.text.TextUtils.appendHexString;
import static me.aap.utils.ui.UiUtils.resizedBitmap;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.provider.FermataContentProvider;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.net.http.HttpFileDownloader.Status;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;
import me.aap.utils.ui.UiUtils;

/**
 * @author Andrey Pavlenko
 */
public class BitmapCache {
	private final File iconsCache;
	private final File imageCache;
	private final String iconsCacheUri;
	private final String imageCacheUri;
	private final SharedPreferences prefs;
	private final Map<String, Ref> cache = new HashMap<>();
	private final ReferenceQueue<Bitmap> refQueue = new ReferenceQueue<>();
	private final PromiseQueue queue = new PromiseQueue(App.get().getExecutor());
	private final Map<String, String> invalidBitmapUris = new ConcurrentHashMap<>();

	public BitmapCache() {
		File cache = App.get().getExternalCacheDir();
		if (cache == null) cache = App.get().getCacheDir();
		iconsCache = new File(cache, "icons").getAbsoluteFile();
		imageCache = new File(cache, "images").getAbsoluteFile();
		iconsCacheUri = Uri.fromFile(iconsCache).toString() + '/';
		imageCacheUri = Uri.fromFile(imageCache).toString() + '/';
		prefs = getContext().getSharedPreferences("image-cache", MODE_PRIVATE);
	}

	public boolean isResourceImageAvailable(Uri uri) {
		try (AssetFileDescriptor afd = openResource(getContext(), uri, 0)) {
			return afd.getLength() > 0;
		} catch (Exception ex) {
			return false;
		}
	}

	public ParcelFileDescriptor openResourceImage(Uri uri) throws FileNotFoundException {
		Context ctx = getContext();
		int size = getIconSize(ctx);
		String iconUri = toIconUri(uri.toString(), size);
		File iconFile = new File(iconsCache, iconUri.substring(iconsCacheUri.length()));

		if (iconFile.isFile()) {
			return ctx.getContentResolver().openFileDescriptor(Uri.parse(iconUri), "r");
		} else if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
			loadBitmap(ctx, uri.toString(), iconUri, true, size);
			return ctx.getContentResolver().openFileDescriptor(Uri.parse(iconUri), "r");
		} else {
			return openResource(ctx, uri, 0).getParcelFileDescriptor();
		}
	}

	@NonNull
	public FutureSupplier<Bitmap> getBitmap(Context ctx, String uri, boolean cache, boolean resize) {
		String orig = FermataContentProvider.getOrigUri(uri);
		String u = (orig == null) ? uri : orig;
		int size;
		String iconUri;
		Bitmap bm;

		if (resize) {
			size = getIconSize(ctx);
			iconUri = toIconUri(u, size);
			bm = getCachedBitmap(iconUri);
		} else {
			size = 0;
			iconUri = null;
			bm = getCachedBitmap(u);
		}

		if (bm != null) return completed(bm);

		if (u.startsWith("http://") || u.startsWith("https://")) {
			return loadHttpBitmap(u, iconUri, size);
		}

		return queue.enqueue(() -> loadBitmap(ctx, u, iconUri, cache, size));
	}

	@Nullable
	private Bitmap getCachedBitmap(String uri) {
		synchronized (cache) {
			clearRefs();
			Ref r = cache.get(uri);

			if (r != null) {
				Bitmap bm = r.get();
				if (bm != null) return bm;
				else cache.remove(uri);
			}

			return null;
		}
	}

	private Bitmap loadBitmap(Context ctx, String uri, String iconUri, boolean cache, int size) {
		Bitmap bm;

		if (iconUri != null) {
			bm = getCachedBitmap(iconUri);
			if (bm != null) return bm;
			File iconFile = new File(iconsCache, iconUri.substring(iconsCacheUri.length()));
			if (iconFile.isFile()) bm = loadBitmap(ctx, iconUri, cache ? uri : null, 0);
			if (bm != null) return bm;
			bm = loadBitmap(ctx, uri, cache ? iconUri : null, size);
			if (cache && (bm != null)) saveIcon(bm, iconFile);
		} else {
			bm = getCachedBitmap(uri);
			if (bm != null) return bm;
			bm = loadBitmap(ctx, uri, cache ? uri : null, size);
		}

		return bm;
	}

	private Bitmap loadBitmap(Context ctx, String uri, String cacheUri, int size) {
		try {
			Bitmap bm = null;
			Uri u = Uri.parse(uri);
			String scheme = u.getScheme();
			if (scheme == null) return null;

			switch (scheme) {
				case "file":
					try (ParcelFileDescriptor fd = ctx.getContentResolver().openFileDescriptor(u, "r")) {
						if (fd != null) bm = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
					}
					break;
				case ContentResolver.SCHEME_ANDROID_RESOURCE:
					Resources res = ctx.getResources();
					String[] s = uri.split("/");
					int id = res.getIdentifier(s[s.length - 1], s[s.length - 2], ctx.getPackageName());
					if (id == 0) id = res.getIdentifier(s[s.length - 1], s[s.length - 2], s[s.length - 3]);
					if (id == 0) {
						Log.e("Resource, ", uri, " not found!");
						break;
					}
					Drawable d = ResourcesCompat.getDrawable(res, id, ctx.getTheme());
					if (d != null) bm = UiUtils.drawBitmap(d, Color.TRANSPARENT, Color.WHITE);
					break;
				case "content":
					bm = loadContentBitmap(ctx, u, size);
					size = 0;
					break;
				default:
					FermataVfsManager vfs = getVfsManager();
					if (vfs.isSupportedScheme(scheme))
						bm = loadUriBitmap(vfs.getHttpRid(Rid.create(u)).toString());
			}

			if (bm == null) return null;
			if (size != 0) bm = resizedBitmap(bm, size);
			return (cacheUri != null) ? cacheBitmap(cacheUri, bm) : bm;
		} catch (Exception ex) {
			Log.d(ex, "Failed to load bitmap: ", uri);
			return null;
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private FutureSupplier<Bitmap> loadHttpBitmap(String uri, String cacheUri, int size) {
		return downloadImage(uri).then(s -> {
			if (s == null) return completedNull();
			try (InputStream is = s.getFileStream(true)) {
				Bitmap bm = BitmapFactory.decodeStream(is);

				if (bm == null) {
					File f = s.getLocalFile();
					if (f != null) f.delete();
					invalidBitmapUris.put(uri, uri);
					return failed(new IOException("Failed to decode image: " + uri));
				} else {
					if (size != 0) bm = resizedBitmap(bm, size);
					if (cacheUri != null) bm = cacheBitmap(cacheUri, bm);
					return completed(bm);
				}
			} catch (Exception ex) {
				invalidBitmapUris.put(uri, uri);
				return failed(ex);
			}
		});
	}

	public FutureSupplier<Status> downloadImage(String uri) {
		if (invalidBitmapUris.containsKey(uri)) {
			Log.d("Invalid bitmap uri: ", uri);
			return completedNull();
		}

		String path;

		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			b.append("/X/");
			appendHexString(b, sha1(uri));
			b.setCharAt(1, b.charAt(3));
			b.append('.').append(getFileExtension(uri, "img"));
			path = b.toString();
		}

		File dst = toImageFile(uri);
		ImagePrefs ip = new ImagePrefs(prefs, path);
		HttpFileDownloader d = new HttpFileDownloader();
		d.setReturnExistingOnFail(true);
		return d.download(uri, dst, ip).onFailure(ex -> {
			Log.d(ex, "Failed to download image: ", uri);
			invalidBitmapUris.put(uri, uri);
		});
	}

	public synchronized FutureSupplier<Uri> addImage(String uri,
																									 CheckedSupplier<Bitmap, Exception> s) {
		File f = toImageFile(uri);
		if (f.isFile()) return completed(Uri.fromFile(f));

		return queue.enqueue(() -> {
			synchronized (BitmapCache.this) {
				if (!f.isFile()) {
					try (OutputStream out = new FileOutputStream(f)) {
						CompressFormat fmt =
								(f.getName().endsWith(".png")) ? CompressFormat.PNG : CompressFormat.JPEG;
						s.get().compress(fmt, 100, out);
					} catch (Exception ex) {
						Log.e(ex, "Failed to save image: ", f);
					}
				}
				return Uri.fromFile(f);
			}
		});
	}

	private Bitmap loadContentBitmap(Context ctx, Uri u, int size) throws IOException {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ContentResolver cr = ctx.getContentResolver();
			if (size == 0) size = getIconSize(ctx);
			return cr.loadThumbnail(u, new Size(size, size), null);
		} else {
			try (AssetFileDescriptor afd = openResource(ctx, u, size)) {
				return (afd == null) ? null : BitmapFactory.decodeFileDescriptor(afd.getFileDescriptor());
			}
		}
	}

	private AssetFileDescriptor openResource(Context ctx, Uri u, int size)
			throws FileNotFoundException {
		if (size == 0) size = getIconSize(ctx);
		ContentResolver cr = ctx.getContentResolver();
		Bundle opts = new Bundle();
		opts.putParcelable(ContentResolver.EXTRA_SIZE, new Point(size, size));
		return cr.openTypedAssetFileDescriptor(u, "image/*", opts, null);
	}

	private Bitmap loadUriBitmap(String uri) throws IOException {
		try (InputStream in = new URL(uri).openStream()) {
			return BitmapFactory.decodeStream(in);
		}
	}

	private Bitmap cacheBitmap(String uri, Bitmap bm) {
		synchronized (cache) {
			clearRefs();
			Ref ref = new Ref(uri, bm, refQueue);
			Ref cachedRef = CollectionUtils.putIfAbsent(cache, uri, ref);

			if (cachedRef != null) {
				Bitmap cached = cachedRef.get();
				if (cached != null) return cached;
				cache.put(uri, ref);
			}

			return bm;
		}
	}

	private static int getIconSize(Context ctx) {
		return 3 * smallIconSize(ctx);
	}

	private static int smallIconSize(Context ctx) {
		switch (ctx.getResources().getConfiguration().densityDpi) {
			case DisplayMetrics.DENSITY_LOW:
				return 32;
			case DisplayMetrics.DENSITY_MEDIUM:
				return 48;
			case DisplayMetrics.DENSITY_HIGH:
				return 72;
			case DisplayMetrics.DENSITY_XHIGH:
				return 96;
			case DisplayMetrics.DENSITY_XXHIGH:
				return 144;
			default:
				return 192;
		}
	}

	String getImageUri(byte[] hash, TextBuilder tb) {
		tb.setLength(0);
		tb.append(imageCacheUri);
		int len = tb.length();
		appendHexString(tb.append("X/"), hash, 0, SHA1_DIGEST_LEN).append(".jpg");
		tb.setCharAt(len, tb.charAt(len + 2));
		return tb.toString().intern();
	}

	synchronized byte[] saveBitmap(Bitmap bm, TextBuilder tb) {
		if (bm == null) return null;

		try {
			MemOutputStream mos = new MemOutputStream(bm.getByteCount());
			if (!bm.compress(CompressFormat.JPEG, 100, mos)) return null;

			byte[] content = mos.trimBuffer();
			MessageDigest md = MessageDigest.getInstance("sha-1");
			md.update(content);
			byte[] digest = md.digest();
			int pos = tb.length();
			appendHexString(tb.append("X/"), digest).append(".jpg");
			tb.setCharAt(pos, tb.charAt(pos + 2));
			File f = new File(imageCache, tb.substring(pos));

			if (!f.isFile()) {
				File dir = f.getParentFile();
				if (dir != null) //noinspection ResultOfMethodCallIgnored
					dir.mkdirs();
				try (OutputStream os = new FileOutputStream(f)) {
					os.write(content);
				}
			}

			return digest;
		} catch (Exception ex) {
			Log.e(ex, "Failed to save image");
			return null;
		}
	}

	private synchronized void saveIcon(Bitmap bm, File f) {
		File p = f.getParentFile();
		if (p != null) //noinspection ResultOfMethodCallIgnored
			p.mkdirs();

		try (OutputStream out = new FileOutputStream(f)) {
			bm.compress(CompressFormat.JPEG, 100, out);
		} catch (Exception ex) {
			Log.e(ex, "Failed to save icon: ", f);
		}
	}

	private String toIconUri(String imageUri, int size) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			tb.append(iconsCacheUri).append(size).append("/X/");
			int len = tb.length();

			if (imageUri.startsWith(imageCacheUri)) {
				tb.append(imageUri.substring(imageCacheUri.length()));
			} else if (imageUri.startsWith(iconsCacheUri)) {
				tb.append(imageUri.substring(iconsCacheUri.length()));
			} else {
				appendHexString(tb, sha1(imageUri)).append(".jpg");
			}

			tb.setCharAt(len - 2, tb.charAt(len));
			return tb.toString();
		}
	}

	private File toImageFile(String uri) {
		String path;
		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			b.append(imageCache);
			int idx = b.length();
			b.append("/X/");
			appendHexString(b, sha1(uri));
			b.setCharAt(idx + 1, b.charAt(idx + 3));
			b.append('.').append(getFileExtension(uri, "img"));
			path = b.toString();
		}
		return new File(path);
	}

	private void clearRefs() {
		for (Ref r = (Ref) refQueue.poll(); r != null; r = (Ref) refQueue.poll()) {
			CollectionUtils.remove(cache, r.key, r);
		}
	}

	private static final class Ref extends SoftReference<Bitmap> {
		final Object key;

		public Ref(String key, Bitmap value, ReferenceQueue<Bitmap> q) {
			super(value, q);
			this.key = key;
		}
	}

	public void cleanUpPrefs() {
		SharedPreferences.Editor edit = prefs.edit();
		boolean removed = false;
		for (String k : new ArrayList<>(prefs.getAll().keySet())) {
			int idx = k.lastIndexOf('#');
			if ((idx <= 0) || (idx == k.length() - 1)) continue;
			File f = new File(imageCache, k.substring(0, idx));
			if (f.exists()) continue;
			Log.i("Image file does not exist - removing preference key ", k);
			edit.remove(k);
			removed = true;
		}
		if (removed) edit.apply();
	}

	private FermataApplication getContext() {
		return FermataApplication.get();
	}

	private FermataVfsManager getVfsManager() {
		return getContext().getVfsManager();
	}

	private static final class ImagePrefs implements SharedPreferenceStore {
		private static final int IMAGE_MAX_AGE = 7 * 24 * 3600;
		private final SharedPreferences prefs;
		private final String id;

		private ImagePrefs(SharedPreferences prefs, String id) {
			this.prefs = prefs;
			this.id = id;
		}

		@Override
		public String getPreferenceKey(Pref<?> pref) {
			return id + '#' + pref.getName();
		}

		@Override
		public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
			return emptyList();
		}

		@NonNull
		@Override
		public SharedPreferences getSharedPreferences() {
			return prefs;
		}

		@Override
		public int getIntPref(Pref<? extends IntSupplier> pref) {
			if (pref.equals(MAX_AGE)) return IMAGE_MAX_AGE;
			return SharedPreferenceStore.super.getIntPref(pref);
		}
	}
}
