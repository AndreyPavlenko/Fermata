package me.aap.fermata.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.utils.io.FileUtils;

import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

/**
 * @author Andrey Pavlenko
 */
public class Utils {

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

	public static boolean isVideoFile(String fileName) {
		return (fileName != null) && isVideoMimeType(FileUtils.getMimeType(fileName));
	}

	public static boolean isVideoMimeType(String mime) {
		return (mime != null) && mime.startsWith("video/");
	}

	@SuppressWarnings("ConstantConditions")
	public static boolean isAutoFlavor() {
		return BuildConfig.AUTO;
	}
}
