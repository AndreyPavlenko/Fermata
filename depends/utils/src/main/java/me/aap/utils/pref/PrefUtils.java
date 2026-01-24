package me.aap.utils.pref;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class PrefUtils {

	public static File getSharedPrefsFile(Context ctx, String name) {
		File file = null;

		try {
			if (VERSION.SDK_INT >= 24) {
				try {
					Method m = ctx.getClass().getMethod("getSharedPreferencesPath", String.class);
					file = (File) m.invoke(ctx, new Object[]{name});
				} catch (Throwable ex) {
					Log.w(ex, "getSharedPreferencesPath failed for ", name);
				}
			}

			if (file == null) {
				Method m = ctx.getClass().getMethod("getSharedPrefsFile", String.class);
				file = (File) m.invoke(ctx, new Object[]{name});
			}
		} catch (Throwable ex) {
			Log.w(ex, "getSharedPrefsFile failed for ", name);
			file = new File(ctx.getFilesDir(), "../shared_prefs/" + name + ".xml").getAbsoluteFile();
		}

		return file;
	}

	public static void exportSharedPrefs(
			Context ctx, Iterable<String> names, ZipOutputStream out) throws IOException {
		for (String n : names) {
			File f = getSharedPrefsFile(ctx, n);

			if (!f.isFile()) {
				Log.d("Shared preferences file does not exist: ", f);
				continue;
			}

			Log.d("Exporting shared preferences: ", n);
			out.putNextEntry(new ZipEntry(n + ".xml"));
			FileUtils.copy(f, out);
			out.closeEntry();
		}
	}

	public static void importSharedPrefs(Context ctx, ZipInputStream in) throws IOException {
		List<File> tmpFiles = new ArrayList<>();

		try {
			for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
				String name = e.getName();
				if (!name.endsWith(".xml")) continue;
				name = name.substring(0, name.length() - 4);
				File dir = getSharedPrefsFile(ctx, name).getParentFile();

				if (dir == null) {
					throw new IOException("Failed to import shared preferences " + name
							+ " - destination directory not found");
				}
				if (!dir.isDirectory() && !dir.mkdirs()) {
					throw new IOException("Failed to import shared preferences " + name
							+ " - unable to create destination directory " + dir);
				}

				File tmp = File.createTempFile(name, "import_prefs.xml", dir);
				tmpFiles.add(tmp);
				FileUtils.copy(in, tmp);
				String tmpName = tmp.getName();
				tmpName = tmpName.substring(0, tmpName.length() - 4);
				SharedPreferences prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
				SharedPreferences tmpPrefs = ctx.getSharedPreferences(tmpName, Context.MODE_PRIVATE);
				Log.d("Importing shared preferences ", name);
				copySharedPrefs(tmpPrefs, prefs);
			}
		} finally {
			for (File f : tmpFiles) {
				if (!f.delete()) {
					Log.w("Failed to delete temporary file ", f);
					f.deleteOnExit();
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static void copySharedPrefs(SharedPreferences from, SharedPreferences to) {
		SharedPreferences.Editor ed = to.edit();
		for (Map.Entry<String, ?> entry : from.getAll().entrySet()) {
			String k = entry.getKey();
			Object v = entry.getValue();
			Log.d(k, '=', v);
			if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
			else if (v instanceof Float) ed.putFloat(k, (Float) v);
			else if (v instanceof Integer) ed.putInt(k, (Integer) v);
			else if (v instanceof Long) ed.putLong(k, (Long) v);
			else if (v instanceof String) ed.putString(k, (String) v);
			else if (v instanceof Set) ed.putStringSet(k, (Set<String>) v);
			else Log.w("Unsupported preference ", k, " of type ", v.getClass());
		}
		ed.apply();
	}
}
