package me.aap.fermata.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.system.Os;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;

import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class Utils {
	private static final StringBuilder sharedStringBuilder = new StringBuilder();

	public static StringBuilder getSharedStringBuilder() {
		assert Thread.currentThread() == Looper.getMainLooper().getThread();
		sharedStringBuilder.setLength(0);
		return sharedStringBuilder;
	}

	public static Uri getResourceUri(Context ctx, int resourceId) {
		Resources res = ctx.getResources();
		return new Uri.Builder()
				.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
				.authority(res.getResourcePackageName(resourceId))
				.appendPath(res.getResourceTypeName(resourceId))
				.appendPath(res.getResourceEntryName(resourceId))
				.build();
	}

	public static Uri getAudioUri(Uri documentUri) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;

		try {
			Uri uri = MediaStore.getMediaUri(FermataApplication.get(), documentUri);
			if (uri == null) return null;

			try (Cursor c = FermataApplication.get().getContentResolver().query(uri,
					new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
				if ((c != null) && c.moveToNext()) {
					return ContentUris.withAppendedId(EXTERNAL_CONTENT_URI, c.getLong(0));
				}
			}
		} catch (Exception ex) {
			Log.d("Utils", "Failed to get audio Uri for " + documentUri, ex);
		}


		return null;
	}

	public static File getFileFromUri(Uri fileUri) {
		ParcelFileDescriptor pfd = null;

		try {
			ContentResolver r = FermataApplication.get().getContentResolver();
			pfd = r.openFileDescriptor(fileUri, "r");
			return (pfd != null) ? getFileFromDescriptor(pfd) : null;
		} catch (Exception ex) {
			Log.d("Utils", "Failed to resolve real path: " + fileUri, ex);
		} finally {
			close(pfd);
		}

		return null;
	}

	public static File getFileFromDescriptor(ParcelFileDescriptor fd) throws Exception {
		String path = Os.readlink("/proc/self/fd/" + fd.getFd());
		File f = new File(path);
		if (f.isFile()) return f;

		if (path.startsWith("/mnt/media_rw/")) {
			f = new File("/storage" + path.substring(13));
			if (f.isFile()) return f;
		}

		return null;
	}

	public static void assertTrue(boolean b) {
		if (BuildConfig.DEBUG && !b) throw new AssertionError();
	}

	public static void showError(String msg) {
		Toast.makeText(FermataApplication.get(), msg, Toast.LENGTH_LONG).show();
	}

	public static void showError(String msg, Throwable err) {
		Log.e("Utils", "Error ocurred", err);
		showError(msg);
	}

	public static void queryText(Context ctx, @StringRes int title, Consumer<CharSequence> result) {
		queryText(ctx, title, "", result);
	}

	public static void queryText(Context ctx, @StringRes int title, CharSequence initText,
															 Consumer<CharSequence> result) {
		EditText text = new EditText(ctx);
		text.setSingleLine();
		text.setText(initText);
		new AlertDialog.Builder(ctx, R.style.AppTheme)
				.setTitle(title).setView(text)
				.setNegativeButton(android.R.string.cancel, (d, i) -> result.accept(null))
				.setPositiveButton(android.R.string.ok, (d, i) -> result.accept(text.getText())).show();
	}

	@SuppressWarnings("UnusedReturnValue")
	public static StringBuilder appendHexString(StringBuilder sb, byte[] bytes) {
		for (final byte b : bytes) {
			int v = b & 0xFF;
			sb.append(HexTable._table[v >>> 4]).append(HexTable._table[v & 0xF]);
		}
		return sb;
	}

	public static String getFileExtension(String fileName) {
		int idx = fileName.lastIndexOf('.');
		return ((idx == -1) || (idx == (fileName.length() - 1))) ? null : fileName.substring(idx + 1);
	}


	public static String getMimeType(String fileName) {
		return getMimeTypeFromExtension(getFileExtension(fileName));
	}

	public static String getMimeTypeFromExtension(String fileExt) {
		return (fileExt == null) ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
	}

	public static boolean isVideoFile(String fileName) {
		return (fileName != null) && isVideoMimeType(getMimeType(fileName));
	}

	public static boolean isVideoMimeType(String mime) {
		return (mime != null) && mime.startsWith("video/");
	}

	public static <T> int indexOf(T[] array, T value) {
		for (int i = 0; i < array.length; i++) {
			if (Objects.equals(value, array[i])) return i;
		}
		return -1;
	}

	public static int indexOf(int[] array, int value) {
		for (int i = 0; i < array.length; i++) {
			if (value == array[i]) return i;
		}
		return -1;
	}

	public static <T> boolean contains(T[] array, T value) {
		return indexOf(array, value) != -1;
	}

	public static <T> boolean contains(Iterable<T> i, Predicate<T> predicate) {
		for (T t : i) {
			if (predicate.test(t)) return true;
		}
		return false;
	}

	public static <T> boolean remove(Iterable<T> i, Predicate<T> predicate) {
		for (Iterator<T> it = i.iterator(); it.hasNext(); ) {
			if (predicate.test(it.next())) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	public static <T> T[] remove(T[] array, int idx) {
		Class<?> type = requireNonNull(array.getClass().getComponentType());
		@SuppressWarnings("unchecked") T[] a = (T[]) Array.newInstance(type, array.length - 1);
		System.arraycopy(array, 0, a, 0, idx);
		if (idx != (array.length - 1)) System.arraycopy(array, idx, a, idx + 1, a.length - idx);
		return a;
	}

	public static <T> void replace(List<T> list, T o, T with) {
		int i = list.indexOf(o);
		if (i != -1) list.set(i, with);
	}

	public static <T> void move(List<T> list, int fromPosition, int toPosition) {
		if (fromPosition < toPosition) {
			for (int i = fromPosition; i < toPosition; i++) {
				Collections.swap(list, i, i + 1);
			}
		} else {
			for (int i = fromPosition; i > toPosition; i--) {
				Collections.swap(list, i, i - 1);
			}
		}
	}

	public static <T> void move(T[] array, int fromPosition, int toPosition) {
		if (fromPosition < toPosition) {
			for (int i = fromPosition; i < toPosition; i++) {
				T t = array[i];
				array[i] = array[i + 1];
				array[i + 1] = t;
			}
		} else {
			for (int i = fromPosition; i > toPosition; i--) {
				T t = array[i];
				array[i] = array[i - 1];
				array[i - 1] = t;
			}
		}
	}

	public static String timeToString(int seconds) {
		StringBuilder sb = getSharedStringBuilder();
		timeToString(sb, seconds);
		return sb.toString();
	}

	public static void timeToString(StringBuilder sb, int seconds) {
		if (seconds < 60) {
			sb.append("00:");
			appendTime(sb, seconds);
		} else if (seconds < 3600) {
			int m = seconds / 60;
			appendTime(sb, m);
			sb.append(':');
			appendTime(sb, seconds - (m * 60));
		} else {
			int h = seconds / 3600;
			appendTime(sb, h);
			sb.append(':');
			timeToString(sb, seconds - (h * 3600));
		}
	}

	private static void appendTime(StringBuilder sb, int time) {
		if (time < 10) sb.append(0);
		sb.append(time);
	}

	public static int stringToTime(String s) {
		String[] values = s.split(":");

		try {
			if (values.length == 2) {
				return Integer.parseInt(values[0]) * 60 + Integer.parseInt(values[1]);
			} else if (values.length == 3) {
				return Integer.parseInt(values[0]) * 3600 + Integer.parseInt(values[1]) * 60
						+ Integer.parseInt(values[2]);
			}
		} catch (NumberFormatException ex) {
			Log.w("Utils", "Invalid time string: " + s, ex);
		}

		return -1;
	}

	public static void close(Closeable... close) {
		if (close != null) {
			for (Closeable c : close) {
				if (c != null) {
					try {
						c.close();
					} catch (IOException ignore) {
					}
				}
			}
		}
	}

	public static int toPx(int dp) {
		return Math.round(dp * Resources.getSystem().getDisplayMetrics().density);
	}

	private static final class HexTable {
		static final char[] _table = {'0', '1', '2', '3', '4', '5', '6', '7',
				'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	}
}
