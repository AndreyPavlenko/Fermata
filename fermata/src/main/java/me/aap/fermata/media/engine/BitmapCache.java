package me.aap.fermata.media.engine;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.io.ByteBufferInputStream;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;
import me.aap.utils.ui.UiUtils;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.security.SecurityUtils.sha1;
import static me.aap.utils.text.TextUtils.appendHexString;
import static me.aap.utils.ui.UiUtils.resizedBitmap;

/**
 * @author Andrey Pavlenko
 */
public class BitmapCache {
	private final MediaLib lib;
	private final File iconsCache;
	private final File imageCache;
	private final String iconsCacheUri;
	private final String imageCacheUri;
	private final Map<String, Ref> cache = new HashMap<>();
	private final ReferenceQueue<Bitmap> refQueue = new ReferenceQueue<>();
	private final PromiseQueue queue = new PromiseQueue(App.get().getExecutor());
	private final Map<String, String> invalidBitmapUris = new ConcurrentHashMap<>();

	public BitmapCache(MediaLib lib) {
		this.lib = lib;
		File cache = App.get().getExternalCacheDir();
		if (cache == null) cache = App.get().getCacheDir();
		iconsCache = new File(cache, "icons").getAbsoluteFile();
		imageCache = new File(cache, "images").getAbsoluteFile();
		iconsCacheUri = Uri.fromFile(iconsCache).toString() + '/';
		imageCacheUri = Uri.fromFile(imageCache).toString() + '/';
	}

	@Nullable
	public Bitmap getCachedBitmap(String uri) {
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

	@NonNull
	public FutureSupplier<Bitmap> getBitmap(Context ctx, String uri, boolean cache, boolean resize) {
		int size;
		String iconUri;
		Bitmap bm;

		if (resize) {
			size = getIconSize(ctx);
			iconUri = toIconUri(uri, size);
			bm = getCachedBitmap(iconUri);
		} else {
			size = 0;
			iconUri = null;
			bm = getCachedBitmap(uri);
		}

		if (bm != null) return completed(bm);

		if (uri.startsWith("http://") || uri.startsWith("https://")) {
			return loadHttpBitmap(uri, iconUri, size);
		}

		return queue.enqueue(() -> loadBitmap(ctx, uri, iconUri, cache, size));
	}

	private Bitmap loadBitmap(Context ctx, String uri, String iconUri, boolean cache, int size) {
		Bitmap bm;

		if (iconUri != null) {
			bm = getCachedBitmap(iconUri);
			if (bm != null) return bm;
			bm = loadBitmap(ctx, uri, cache ? iconUri : null, size);
			if (cache && (bm != null)) saveIcon(bm, iconUri);
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
					try (ParcelFileDescriptor fd = ctx.getContentResolver().openFileDescriptor(Uri.parse(uri), "r")) {
						if (fd != null) bm = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
					}
					break;
				case ContentResolver.SCHEME_ANDROID_RESOURCE:
					Resources res = ctx.getResources();
					String[] s = uri.split("/");
					int id = res.getIdentifier(s[s.length - 1], s[s.length - 2], ctx.getPackageName());
					Drawable d = ResourcesCompat.getDrawable(res, id, ctx.getTheme());
					if (d != null) bm = UiUtils.drawBitmap(d, Color.TRANSPARENT, Color.WHITE);
					break;
				case "content":
					bm = loadContentBitmap(ctx, uri);
					break;
				default:
					FermataVfsManager vfs = lib.getVfsManager();
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

	private FutureSupplier<Bitmap> loadHttpBitmap(String uri, String cacheUri, int size) {
		if (invalidBitmapUris.containsKey(uri)) return completedNull();

		Promise<Bitmap> p = new Promise<>();

		HttpConnection.connect(o -> {
			o.url(uri);
			o.responseTimeout = 10;
		}, (resp, err) -> {
			if (err != null) {
				p.completeExceptionally(err);
				return p;
			}

			return resp.getPayload((payload, fail) -> {
				if (fail != null) {
					p.completeExceptionally(fail);
					return p;
				}

				Bitmap bm = BitmapFactory.decodeStream(new ByteBufferInputStream(payload));

				if (bm == null) {
					invalidBitmapUris.put(uri, uri);
					p.completeExceptionally(new IOException("Failed to load bitmap: " + uri));
				} else {
					if (size != 0) bm = resizedBitmap(bm, size);
					if (cacheUri != null) bm = cacheBitmap(cacheUri, bm);
					p.complete(bm);
				}

				return p;
			});
		});

		return p;
	}

	private Bitmap loadContentBitmap(Context ctx, String uri) throws IOException {
		ContentResolver cr = ctx.getContentResolver();
		Uri u = Uri.parse(uri);
		int s = getIconSize(ctx);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			return cr.loadThumbnail(u, new Size(s, s), null);
		} else {
			final Bundle opts = new Bundle();
			opts.putParcelable(ContentResolver.EXTRA_SIZE, new Point(s, s));

			try (AssetFileDescriptor afd = cr.openTypedAssetFileDescriptor(u, "image/*", opts, null)) {
				if (afd == null) return null;
				return BitmapFactory.decodeFileDescriptor(afd.getFileDescriptor());
			}
		}
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
		appendHexString(tb.append("X/"), hash).append(".jpg");
		tb.setCharAt(len, tb.charAt(len + 2));
		return tb.toString().intern();
	}

	byte[] saveBitmap(Bitmap bm, TextBuilder tb) {
		if (bm == null) return null;

		try {
			MemOutputStream mos = new MemOutputStream(bm.getByteCount());
			if (!bm.compress(Bitmap.CompressFormat.JPEG, 100, mos)) return null;

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

	private void saveIcon(Bitmap bm, String uri) {
		File f = new File(iconsCache, uri.substring(iconsCacheUri.length()));
		File p = f.getParentFile();
		if (p != null) //noinspection ResultOfMethodCallIgnored
			p.mkdirs();

		try (OutputStream out = new FileOutputStream(f)) {
			bm.compress(Bitmap.CompressFormat.JPEG, 100, out);
		} catch (Exception ex) {
			Log.e(ex, "Failed to save icon: ", uri);
		}
	}

	private String toIconUri(String imageUri, int size) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			tb.append(iconsCacheUri).append(size).append("/X/");
			int len = tb.length();

			if ((imageUri.startsWith(imageCacheUri)) || (imageUri.startsWith(iconsCacheUri))) {
				tb.append(imageUri.substring(imageUri.lastIndexOf('/') + 1));
			} else {
				appendHexString(tb, sha1(imageUri)).append(".jpg");
			}

			tb.setCharAt(len - 2, tb.charAt(len));
			return tb.toString();
		}
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
}
