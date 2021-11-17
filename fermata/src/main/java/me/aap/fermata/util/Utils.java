package me.aap.fermata.util;

import static android.content.Context.UI_MODE_SERVICE;
import static android.content.res.Configuration.UI_MODE_TYPE_NORMAL;

import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
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

	// A workaround for Resources$NotFoundException
	public static Context dynCtx(Context ctx) {
		if (!BuildConfig.AUTO) SplitCompat.install(ctx);
		return ctx;
	}

	public static boolean isSafSupported(MainActivityDelegate a) {
		if (a.isCarActivity()) return false;
		Context ctx = a.getContext();
		UiModeManager umm = (UiModeManager) a.getContext().getSystemService(UI_MODE_SERVICE);
		if (umm.getCurrentModeType() != UI_MODE_TYPE_NORMAL) return false;
		Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		return i.resolveActivity(ctx.getPackageManager()) != null;
	}
}
