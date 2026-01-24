package me.aap.utils.module;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.content.Context.NOTIFICATION_SERVICE;
import static me.aap.utils.function.ProgressiveResultConsumer.progressShift;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.play.core.splitcompat.SplitCompat;
import com.google.android.play.core.splitinstall.SplitInstallException;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

import java.util.Collections;

import javax.annotation.Nonnull;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class DynamicModuleInstaller {
	private static final String TAG = "me.aap.utils.module.install";
	private final Activity activity;
	@DrawableRes
	int smallIcon;
	@Nonnull
	private String title = "";
	@Nonnull
	private String pendingMessage = "Pending";
	@Nonnull
	private String downloadingMessage = "Downloading";
	@Nonnull
	private String installingMessage = "Installing";
	@Nonnull
	private String notificationChannel = TAG;

	public DynamicModuleInstaller(Activity activity) {
		this.activity = activity;
	}

	public void setSmallIcon(@DrawableRes int smallIcon) {
		this.smallIcon = smallIcon;
	}

	public void setTitle(@Nonnull String title) {
		this.title = title;
	}

	public void setPendingMessage(@Nonnull String pendingMessage) {
		this.pendingMessage = pendingMessage;
	}

	public void setDownloadingMessage(@Nonnull String downloadingMessage) {
		this.downloadingMessage = downloadingMessage;
	}

	public void setInstallingMessage(@Nonnull String installingMessage) {
		this.installingMessage = installingMessage;
	}

	public void setNotificationChannel(@NonNull String id, @NonNull String name) {
		this.notificationChannel = id;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel nc = new NotificationChannel(id, name, IMPORTANCE_LOW);
			NotificationManager nmgr = (NotificationManager) activity.getSystemService(NOTIFICATION_SERVICE);
			if (nmgr != null) nmgr.createNotificationChannel(nc);
		}
	}

	public FutureSupplier<Void> install(String moduleName) {
		SplitInstallManager sm = SplitInstallManagerFactory.create(activity);
		SplitInstallRequest req = SplitInstallRequest.newBuilder().addModule(moduleName).build();

		InstallPromise p = new InstallPromise(sm, moduleName);
		sm.registerListener(p);
		sm.startInstall(req)
				.addOnSuccessListener(id -> {
					if (id == 0) {
						p.complete(null);
						sm.unregisterListener(p);
					} else {
						p.sessionId = id;
					}
				}).addOnFailureListener(p::completeExceptionally);

		p.thenRun(() -> {
			sm.unregisterListener(p);
			p.notif.cancel(TAG, p.sessionId);
		});

		return p;
	}

	public FutureSupplier<Void> uninstall(String moduleName) {
		Promise<Void> p = new Promise<>();
		SplitInstallManager sm = SplitInstallManagerFactory.create(activity);
		sm.deferredUninstall(Collections.singletonList(moduleName))
				.addOnSuccessListener(r -> p.complete(null))
				.addOnFailureListener(p::completeExceptionally);
		return p;
	}

	private final class InstallPromise extends Promise<Void> implements SplitInstallStateUpdatedListener {
		final SplitInstallManager sm;
		final String moduleName;
		final NotificationManagerCompat notif;
		final NotificationCompat.Builder notification;
		int sessionId;

		public InstallPromise(SplitInstallManager sm, String moduleName) {
			this.sm = sm;
			this.moduleName = moduleName;
			notif = NotificationManagerCompat.from(activity);
			notification = new NotificationCompat.Builder(activity, notificationChannel);
			notification.setSmallIcon(smallIcon).setContentTitle(title);
		}

		@Override
		public void onStateUpdate(@NonNull SplitInstallSessionState st) {
			if ((sessionId == 0) && !st.moduleNames().contains(moduleName)) return;
			else if (st.sessionId() != sessionId) return;

			switch (st.status()) {
				case SplitInstallSessionStatus.UNKNOWN:
				default:
					return;
				case SplitInstallSessionStatus.PENDING:
					notification.setContentTitle(pendingMessage);
					break;
				case SplitInstallSessionStatus.DOWNLOADING:
					long total = st.totalBytesToDownload();
					int shift = progressShift(total);
					notification.setContentTitle(downloadingMessage);
					notification.setProgress((int) (total >> shift), (int) (st.bytesDownloaded() >> shift), false);
					break;
				case SplitInstallSessionStatus.DOWNLOADED:
				case SplitInstallSessionStatus.INSTALLING:
					notification.setContentTitle(installingMessage);
					break;
				case SplitInstallSessionStatus.INSTALLED:
					SplitCompat.install(activity.getApplicationContext());
					SplitCompat.installActivity(activity);
					complete(null);
					return;
				case SplitInstallSessionStatus.FAILED:
					completeExceptionally(new SplitInstallException(st.errorCode()));
					return;
				case SplitInstallSessionStatus.CANCELED:
				case SplitInstallSessionStatus.CANCELING:
					cancel();
					return;
				case SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION:
					try {
						sm.startConfirmationDialogForResult(st, activity, 123);
					} catch (Exception ex) {
						Log.e(ex, "Failed to request user confirmation");
						completeExceptionally(ex);
					}

					return;
			}

			notif.notify(TAG, sessionId, notification.build());
		}
	}
}
