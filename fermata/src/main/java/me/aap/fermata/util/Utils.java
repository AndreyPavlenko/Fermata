package me.aap.fermata.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;

import me.aap.fermata.R;
import me.aap.utils.io.FileUtils;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.ui.notif.HttpDownloadStatusListener;

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

	public static boolean isVideoFile(String fileName) {
		return (fileName != null) && isVideoMimeType(FileUtils.getMimeType(fileName));
	}

	public static boolean isVideoMimeType(String mime) {
		return (mime != null) && mime.startsWith("video/");
	}

	public static HttpFileDownloader createDownloader(Context ctx, String url) {
		HttpFileDownloader d = new HttpFileDownloader();
		HttpDownloadStatusListener l = new HttpDownloadStatusListener(ctx);
		l.setSmallIcon(R.drawable.ic_notification);
		l.setTitle(ctx.getResources().getString(R.string.downloading, url));
		l.setFailureTitle(s -> ctx.getResources().getString(R.string.err_failed_to_download, url));
		d.setStatusListener(l);
		return d;
	}
}
