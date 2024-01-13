package me.aap.fermata.auto;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
import static android.os.Build.VERSION.SDK_INT;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

public class ProjectionService extends Service {
	private static final String CAPTURE_INTENT = "CaptureIntent";
	private static Promise<MediaProjection> promise;

	static FutureSupplier<MediaProjection> start() {
		if (promise != null) promise.cancel();
		var p = promise = new Promise<>();
		p.thenRun(() -> {
			if (promise == p) promise = null;
		});
		var start = ProjectionActivity.start().onCompletion((i, err) -> {
			if (promise != p) return;
			if (err == null) {
				try {
					Log.i("Starting ProjectionService");
					var ctx = FermataApplication.get();
					var intent = new Intent(ctx, ProjectionService.class);
					var extras = new Bundle();
					extras.putParcelable(CAPTURE_INTENT, i);
					intent.putExtras(extras);
					if (SDK_INT >= VERSION_CODES.O) ctx.startForegroundService(intent);
					else ctx.startService(intent);
				} catch (Exception startErr) {
					Log.e(startErr, "Failed to start ProjectionService");
					promise = null;
					p.completeExceptionally(startErr);
				}
			} else {
				p.completeExceptionally(err);
			}
		});
		p.onCancel(start::cancel);
		return p;
	}

	static void stop() {
		if (promise != null) {
			promise.cancel();
			promise = null;
		}
		try {
			Log.i("Stopping ProjectionService");
			var ctx = FermataApplication.get();
			ctx.stopService(new Intent(ctx, ProjectionService.class));
		} catch (Exception err) {
			Log.d(err, "Failed to stop ProjectionService");
		}
	}

	@Override
	public void onCreate() {
		try {
			var chid = "FermataMirror";
			var name = getString(R.string.mirror_service_name);
			if (VERSION.SDK_INT >= VERSION_CODES.O) {
				var nmgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				if (nmgr != null) {
					nmgr.createNotificationChannel(
							new NotificationChannel(chid, name, NotificationManager.IMPORTANCE_LOW));
				}
			}
			var notif = new NotificationCompat.Builder(this, chid).setVisibility(
							NotificationCompat.VISIBILITY_PUBLIC).setSmallIcon(R.drawable.notification)
					.setContentTitle(name).setColorized(true).setPriority(NotificationCompat.PRIORITY_HIGH)
					.setShowWhen(false).setOnlyAlertOnce(true).setSilent(true).build();
			if (SDK_INT >= VERSION_CODES.Q) {
				startForeground(2, notif, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
			} else {
				startForeground(2, notif);
			}
		} catch (Exception err) {
			Log.e(err, "Failed to start foreground ProjectionService");
			var p = promise;
			promise = null;
			if (p != null) p.completeExceptionally(err);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		var p = promise;
		if (p != null) {
			promise = null;
			var ex = intent.getExtras();
			if (ex != null) {
				Intent i;
				if (SDK_INT >= VERSION_CODES.TIRAMISU) i = ex.getParcelable(CAPTURE_INTENT, Intent.class);
				else i = (Intent) ex.get(CAPTURE_INTENT);
				if (i != null) {
					Log.i("Creating MediaProjection: ", i);
					var mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
					p.complete(mpm.getMediaProjection(Activity.RESULT_OK, i));
				}
			}
			p.complete(null);
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (promise != null) {
			promise.cancel();
			promise = null;
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
