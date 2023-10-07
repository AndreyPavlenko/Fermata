package me.aap.fermata.media.service;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.service.ControlServiceConnection.ACTION_CONTROL_SERVICE;
import static me.aap.utils.misc.MiscUtils.isPackageInstalled;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import java.util.List;
import java.util.concurrent.TimeUnit;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataMediaServiceAddon;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.utils.app.App;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;


/**
 * @author Andrey Pavlenko
 */
public class FermataMediaService extends MediaBrowserServiceCompat implements SharedConstants {
	public static final String ACTION_MEDIA_SERVICE = "me.aap.fermata.action.MediaService";
	public static final String ACTION_CAR_MEDIA_SERVICE = "me.aap.fermata.action.CarMediaService";
	public static final String INTENT_ATTR_NOTIF_COLOR = "me.aap.fermata.notif.color";
	public static final String DEFAULT_NOTIF_COLOR = "#546e7a";
	private static final int INTENT_CODE = 1;
	private static final String INTENT_PREV = "me.aap.fermata.action.prev";
	private static final String INTENT_RW = "me.aap.fermata.action.rw";
	private static final String INTENT_STOP = "me.aap.fermata.action.stop";
	private static final String INTENT_PLAY = "me.aap.fermata.action.play";
	private static final String INTENT_PAUSE = "me.aap.fermata.action.pause";
	private static final String INTENT_FF = "me.aap.fermata.action.ff";
	private static final String INTENT_NEXT = "me.aap.fermata.action.next";
	private static final String INTENT_FAVORITE_ADD = "me.aap.fermata.action.favorite.add";
	private static final String INTENT_FAVORITE_REMOVE = "me.aap.fermata.action.favorite.remove";
	private static final String EXTRA_MEDIA_SEARCH_SUPPORTED =
			"android.media.browse.SEARCH_SUPPORTED";
	private static final int NOTIF_ID = 1;
	private static final String NOTIF_CHANNEL_ID = "Fermata";
	private DefaultMediaLib lib;
	private MediaSessionCompat session;
	MediaSessionCallback callback;
	FermataToControlConnection controlConnection;

	private BroadcastReceiver intentReceiver;
	private int notifColor;
	private PendingIntent notifContentIntent;
	private MediaStyle notifStyle;
	private Action actionPrev;
	private Action actionRw;
	private Action actionPlay;
	private Action actionPause;
	private Action actionFf;
	private Action actionNext;
	private Action actionFavAdd;
	private Action actionFavRm;
	private Bitmap defaultAudioIcon;
	private Bitmap defaultVideoIcon;

	public MediaLib getLib() {
		return lib;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Context ctx = this;
		lib = new DefaultMediaLib(FermataApplication.get());
		session = new MediaSessionCompat(this, "FermataMediaService");
		setSessionToken(session.getSessionToken());
		callback = new MediaSessionCallback(this, session, lib,
				PlaybackControlPrefs.create(FermataApplication.get().getDefaultSharedPreferences()),
				FermataApplication.get().getHandler());
		session.setCallback(callback);

		Intent mediaButtonIntent =
				new Intent(Intent.ACTION_MEDIA_BUTTON, null, ctx, MediaButtonReceiver.class);
		session.setMediaButtonReceiver(
				PendingIntent.getBroadcast(ctx, 0, mediaButtonIntent, FLAG_IMMUTABLE));
		notifColor = Color.parseColor(DEFAULT_NOTIF_COLOR);
		App.get().getScheduler().schedule(lib::cleanUpPrefs, 1, TimeUnit.HOURS);
		Log.d("FermataMediaService created");
		for (FermataAddon a : AddonManager.get().getAddons()) {
			if (a instanceof FermataMediaServiceAddon)
				((FermataMediaServiceAddon) a).onServiceCreate(callback);
		}
	}

	@Override
	public void onDestroy() {
		for (FermataAddon a : AddonManager.get().getAddons()) {
			if (a instanceof FermataMediaServiceAddon)
				((FermataMediaServiceAddon) a).onServiceDestroy(callback);
		}
		super.onDestroy();
		NotificationManagerCompat.from(this).cancel(NOTIF_ID);
		if (intentReceiver != null) unregisterReceiver(intentReceiver);
		if (controlConnection != null) controlConnection.disconnect(true);
		intentReceiver = null;
		controlConnection = null;
		callback.close();
		session.release();
		Log.d("FermataMediaService destroyed");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		MediaButtonReceiver.handleIntent(session, intent);
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		String action = intent.getAction();
		if (action == null) return super.onBind(intent);

		switch (action) {
			case ACTION_CAR_MEDIA_SERVICE:
				if (BuildConfig.AUTO) connectToControl();
			case ACTION_MEDIA_SERVICE:
				notifColor = intent.getIntExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
				return new ServiceBinder();
			case ACTION_CONTROL_SERVICE:
				if (BuildConfig.AUTO) return connectToControl();
		}

		return super.onBind(intent);
	}

	private IBinder connectToControl() {
		String pkg = FermataToControlConnection.getPkgId(this);
		if (!isPackageInstalled(this, pkg)) return null;

		if (controlConnection == null) {
			controlConnection = new FermataToControlConnection(this);
			controlConnection.connect();
		}

		return controlConnection.getBinder();
	}

	@Override
	public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
															 Bundle rootHints) {
		Bundle extras = new Bundle();
		extras.putBoolean(EXTRA_MEDIA_SEARCH_SUPPORTED, true);
		extras.putBoolean(CONTENT_STYLE_SUPPORTED, true);
		extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
		extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
		return new BrowserRoot(getLib().getRootId(), extras);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onLoadChildren(@NonNull String parentMediaId,
														 @NonNull Result<List<MediaItem>> result) {
		getLib().getChildren(parentMediaId, result);
	}

	@Override
	public void onLoadItem(String itemId, @NonNull Result<MediaItem> result) {
		getLib().getItem(itemId, result);
	}

	@Override
	public void onSearch(@NonNull String query, Bundle extras,
											 @NonNull Result<List<MediaItem>> result) {
		getLib().search(query, result);
	}

	@Override
	public void onLowMemory() {
		if (lib != null) lib.clearCache();
	}

	void updateSessionState(PlaybackStateCompat playbackState, MediaMetadataCompat meta,
													List<MediaSessionCompat.QueueItem> queue, int repeat, int shuffle) {
		if (BuildConfig.AUTO && (controlConnection != null)) {
			MediaSessionState st = new MediaSessionState(playbackState, meta, queue, repeat, shuffle);
			controlConnection.sendPlaybackState(st);
		}
	}

	@SuppressLint("SwitchIntDef")
	void updateNotification(int st, PlayableItem currentItem) {
		switch (st) {
			case PlaybackStateCompat.STATE_NONE:
			case PlaybackStateCompat.STATE_STOPPED:
			case PlaybackStateCompat.STATE_ERROR:
				stopForeground(true);
				break;
			case PlaybackStateCompat.STATE_PAUSED:
				if (ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
					return;
				}
				NotificationManagerCompat.from(this).notify(NOTIF_ID, createNotification(st, currentItem));
				stopForeground(false);
				break;
			case PlaybackStateCompat.STATE_PLAYING:
				startForeground(NOTIF_ID, createNotification(st, currentItem));
				break;
			default:
				break;
		}
	}

	private Notification createNotification(int st, PlayableItem i) {
		notificationInit();

		Context ctx = this;
		MediaControllerCompat controller = session.getController();
		MediaMetadataCompat mediaMetadata = controller.getMetadata();
		NotificationCompat.Builder builder =
				new NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID).setContentIntent(notifContentIntent)
						.setDeleteIntent(pi(INTENT_STOP)).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
						.setStyle(notifStyle).setSmallIcon(R.drawable.notification).setColor(notifColor)
						.setPriority(NotificationCompat.PRIORITY_HIGH).setShowWhen(false)
						.setOnlyAlertOnce(true);

		if (mediaMetadata != null) {
			MediaDescriptionCompat description = mediaMetadata.getDescription();
			Bitmap largeIcon = description.getIconBitmap();
			builder.setContentTitle(description.getTitle()).setContentText(description.getSubtitle())
					.setSubText(description.getDescription());

			if (callback.isDefaultImage(largeIcon)) {
				if (i.isVideo()) {
					if (defaultVideoIcon == null) defaultVideoIcon = createLargeIcon(R.drawable.video);
					largeIcon = defaultVideoIcon;
				} else {
					if (defaultAudioIcon == null) defaultAudioIcon = createLargeIcon(R.drawable.audiotrack);
					largeIcon = defaultAudioIcon;
				}
			}

			builder.setLargeIcon(largeIcon);
		}

		builder.addAction(actionPrev).addAction(actionRw)
				.addAction((st == PlaybackStateCompat.STATE_PLAYING) ? actionPause : actionPlay)
				.addAction(actionFf).addAction(actionNext)
				.addAction(((i != null) && i.isFavoriteItem()) ? actionFavRm : actionFavAdd);

		return builder.build();
	}

	private Bitmap createLargeIcon(@DrawableRes int icon) {
		Resources res = getResources();
		int w = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
		int h = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
		int s = Math.max(w, h);
		int min = UiUtils.toIntPx(this, 16);
		int max = UiUtils.toIntPx(this, 128);
		if (s < min) s = min;
		else if (s > max) s = max;
		return UiUtils.drawBitmap(requireNonNull(AppCompatResources.getDrawable(this, icon)),
				notifColor, Color.WHITE, s, s);
	}

	public void notificationInit() {
		if (notifContentIntent != null) return;

		try {
			Intent i = new Intent(this, Class.forName("me.aap.fermata.ui.activity.MainActivity"));
			notifContentIntent =
					PendingIntent.getActivity(this, 0, i, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
		} catch (ClassNotFoundException ex) {
			Log.e(ex);
			notifContentIntent = session.getController().getSessionActivity();
		}

		actionPrev = new Action(R.drawable.prev, getString(R.string.prev), pi(INTENT_PREV));
		actionRw = new Action(R.drawable.rw, getString(R.string.rewind), pi(INTENT_RW));
		actionPause = new Action(R.drawable.pause, getString(R.string.pause), pi(INTENT_PAUSE));
		actionPlay = new Action(R.drawable.play, getString(R.string.play), pi(INTENT_PLAY));
		actionFf = new Action(R.drawable.ff, getString(R.string.fast_forward), pi(INTENT_FF));
		actionNext = new Action(R.drawable.next, getString(R.string.next), pi(INTENT_NEXT));
		actionFavAdd =
				new Action(R.drawable.favorite, getString(R.string.favorites_add),
						pi(INTENT_FAVORITE_ADD));
		actionFavRm = new Action(R.drawable.favorite_filled, getString(R.string.favorites_remove),
				pi(INTENT_FAVORITE_REMOVE));

		notifStyle = new MediaStyle().setShowActionsInCompactView(0, 2, 4).setShowCancelButton(true)
				.setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
						PlaybackStateCompat.ACTION_STOP)).setMediaSession(session.getSessionToken());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel nc =
					new NotificationChannel(NOTIF_CHANNEL_ID, getString(R.string.media_service_name),
							NotificationManager.IMPORTANCE_LOW);
			NotificationManager nmgr =
					(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (nmgr != null) nmgr.createNotificationChannel(nc);
		}

		intentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action == null) return;

				switch (action) {
					case INTENT_PREV:
						callback.onSkipToPrevious();
						break;
					case INTENT_RW:
						callback.onRewind();
						break;
					case INTENT_STOP:
						callback.onStop();
						break;
					case INTENT_PLAY:
						callback.onPlay();
						break;
					case INTENT_PAUSE:
						callback.onPause();
						break;
					case INTENT_FF:
						callback.onFastForward();
						break;
					case INTENT_NEXT:
						callback.onSkipToNext();
						break;
					case INTENT_FAVORITE_ADD:
						callback.favoriteAddRemove(true);
						break;
					case INTENT_FAVORITE_REMOVE:
						callback.favoriteAddRemove(false);
						break;
				}
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(INTENT_PREV);
		filter.addAction(INTENT_RW);
		filter.addAction(INTENT_STOP);
		filter.addAction(INTENT_PLAY);
		filter.addAction(INTENT_PAUSE);
		filter.addAction(INTENT_FF);
		filter.addAction(INTENT_NEXT);
		filter.addAction(INTENT_FAVORITE_ADD);
		filter.addAction(INTENT_FAVORITE_REMOVE);

		ContextCompat.registerReceiver(this, intentReceiver, filter, RECEIVER_NOT_EXPORTED);
	}

	private PendingIntent pi(String action) {
		Intent intent = new Intent(action);
		return PendingIntent.getBroadcast(this, INTENT_CODE, intent, FLAG_IMMUTABLE);
	}

	public final class ServiceBinder extends Binder {
		public MediaSessionCallback getMediaSessionCallback() {
			return callback;
		}
	}
}
