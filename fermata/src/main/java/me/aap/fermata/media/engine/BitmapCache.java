package me.aap.fermata.media.engine;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;
import me.aap.utils.ui.UiUtils;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.text.TextUtils.appendHexString;
import static me.aap.utils.ui.UiUtils.resizedBitmap;

/**
 * @author Andrey Pavlenko
 */
public class BitmapCache {
	private final File imageCache;
	private final String imageCacheUri;
	private final Map<String, Ref> cache = new HashMap<>();
	private final ReferenceQueue<Bitmap> refQueue = new ReferenceQueue<>();

	public BitmapCache() {
		File cache = App.get().getExternalCacheDir();
		imageCache = new File(cache, "images").getAbsoluteFile();
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
		Bitmap bm = getCachedBitmap(uri);
		if (bm != null) return completed(bm);
		return App.get().execute(() -> loadBitmap(ctx, uri, cache, resize));
	}

	private Bitmap loadBitmap(Context ctx, String uri, boolean cache, boolean resize) {
		Bitmap bm = getCachedBitmap(uri);
		if (bm != null) return bm;

		if (uri.startsWith("http://") || uri.startsWith("https://")) {
			try (InputStream in = new URL(uri).openStream()) {
				bm = BitmapFactory.decodeStream(in);
			} catch (Exception ex) {
				Log.d(getClass().getName(), "Failed to load bitmap: " + uri, ex);
			}
		} else if (uri.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
			try {
				Resources res = ctx.getResources();
				String[] s = uri.split("/");
				int id = res.getIdentifier(s[s.length - 1], s[s.length - 2], ctx.getPackageName());
				Drawable d = res.getDrawable(id, ctx.getTheme());
				if (d != null) bm = UiUtils.drawBitmap(d, Color.TRANSPARENT, Color.WHITE);
			} catch (Exception ex) {
				Log.e(getClass().getName(), "Failed to load bitmap: " + uri, ex);
			}
		} else {
			ContentResolver cr = ctx.getContentResolver();
			try (ParcelFileDescriptor fd = cr.openFileDescriptor(Uri.parse(uri), "r")) {
				if (fd != null) bm = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
			} catch (Exception ex) {
				Log.d(getClass().getName(), "Failed to load bitmap: " + uri, ex);
			}
		}

		if (bm == null) return null;
		if (resize) bm = resizedBitmap(bm, getIconSize(ctx));
		return cache ? cacheBitmap(uri, bm) : bm;
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

	private int getIconSize(Context ctx) {
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

	public FutureSupplier<String> addBitmap(Bitmap bm) {
		return App.get().execute(() -> saveBitmap(bm));
	}

	private String saveBitmap(Bitmap bm) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			tb.append(imageCacheUri);
			byte[] hash = saveBitmap(bm, tb);
			return (hash == null) ? null : tb.toString();
		}
	}

	String getImageUri(byte[] hash, TextBuilder tb) {
		tb.setLength(0);
		tb.append(imageCacheUri);
		int len = tb.length();
		appendHexString(tb.append("/X/"), hash).append(".jpg");
		tb.setCharAt(len + 1, tb.charAt(len + 3));
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
			Log.e(getClass().getName(), "Failed to save image", ex);
			return null;
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
