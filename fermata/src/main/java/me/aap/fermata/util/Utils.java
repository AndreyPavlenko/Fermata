package me.aap.fermata.util;

import static android.os.Build.VERSION.SDK_INT;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Environment;

import androidx.annotation.Nullable;

import com.google.android.play.core.splitcompat.SplitCompat;

import java.io.File;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.ui.activity.ActivityDelegate;
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
		l.setSmallIcon(R.drawable.notification);
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

	public static boolean isSafSupported(@Nullable MainActivityDelegate a) {
		if ((a != null) && a.isCarActivity()) return false;
		return MiscUtils.isSafSupported();
	}

	public static boolean isExternalStorageManager() {
		return (SDK_INT >= VERSION_CODES.R) && Environment.isExternalStorageManager();
	}

	public static File getAddonsFileDir(AddonInfo i) {
		Context ctx = App.get();
		String name = "addons/" + i.getModuleName();
		File f = ctx.getExternalFilesDir(name);
		return ((f == null) ? new File(ctx.getFilesDir(), name) : f);
	}

	public static File getAddonsCacheDir(AddonInfo i) {
		Context ctx = App.get();
		File f = ctx.getExternalCacheDir();
		return new File((f == null) ? ctx.getCacheDir() : f, "addons/" + i.getModuleName());
	}

	public static void send(ActivityDelegate a, Uri uri, String title, String mime) {
		Intent i = new Intent();
		i.setAction(Intent.ACTION_SEND);
		i.putExtra(Intent.EXTRA_STREAM, uri);
		i.setType(mime);
		a.startActivity(Intent.createChooser(i, title));
	}

	public static boolean openUrl(Context ctx, String url) {
		MainActivityDelegate a = MainActivityDelegate.get(ctx);
		boolean gmap = url.contains("google.") && url.contains("/map");

		if (!gmap && a.isCarActivity()) return openUrlInBrowserFragment(ctx, url);

		Uri u = Uri.parse(url);
		Intent intent = new Intent(Intent.ACTION_VIEW, u);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (gmap) intent.setPackage("com.google.android.apps.maps");

		try {
			a.startActivity(intent);
			return true;
		} catch (ActivityNotFoundException ex) {
			if (openUrlInBrowserFragment(ctx, url)) return true;

			String msg = ctx.getResources().getString(R.string.err_failed_open_url, u);
			a.createDialogBuilder().setMessage(msg)
					.setPositiveButton(android.R.string.ok, null).show();
			return false;
		}
	}

	public static boolean openUrlInBrowserFragment(Context ctx, String url) {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(ctx);
			a.showFragment(R.id.web_browser_fragment).setInput(url);
			return true;
		} catch (Exception ex) {
			Log.d(ex);
			return false;
		}
	}
}
