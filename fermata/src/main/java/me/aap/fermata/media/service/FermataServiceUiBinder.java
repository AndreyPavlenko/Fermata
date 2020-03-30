package me.aap.fermata.media.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataMediaService.ServiceBinder;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.text.TextUtils;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.service.FermataMediaService.ACTION_CAR_MEDIA_SERVICE;
import static me.aap.fermata.media.service.FermataMediaService.ACTION_MEDIA_SERVICE;
import static me.aap.fermata.media.service.FermataMediaService.INTENT_ATTR_NOTIF_COLOR;

/**
 * @author Andrey Pavlenko
 */
public class FermataServiceUiBinder extends BasicEventBroadcaster<FermataServiceUiBinder.Listener>
		implements ServiceConnection, OnSeekBarChangeListener {
	private final Context ctx;
	private PlayableItem currentItem;
	private BiConsumer<FermataServiceUiBinder, Throwable> resultHandler;
	private boolean bound;
	private MediaSessionCallback sessionCallback;
	private MediaControllerCallback callback;
	private MediaControllerCompat mediaController;
	@Nullable
	private View playPauseButton;
	@Nullable
	private View prevButton;
	@Nullable
	private View nextButton;
	@Nullable
	private View rwButton;
	@Nullable
	private View ffButton;
	@Nullable
	private SeekBar progressBar;
	@Nullable
	private TextView progressTime;
	@Nullable
	private TextView progressTotal;
	@Nullable
	private View controlPanel;

	private FermataServiceUiBinder(@NonNull Context ctx, @NonNull BiConsumer<FermataServiceUiBinder, Throwable> resultHandler) {
		this.ctx = ctx;
		this.resultHandler = resultHandler;
	}

	public static void bind(@NonNull Context ctx, int notifColor, boolean isAuto,
													@NonNull BiConsumer<FermataServiceUiBinder, Throwable> resultHandler) {
		FermataServiceUiBinder con = new FermataServiceUiBinder(ctx, resultHandler);
		Intent i = new Intent(ctx, FermataMediaService.class);
		i.setAction(isAuto ? ACTION_CAR_MEDIA_SERVICE : ACTION_MEDIA_SERVICE);
		i.putExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);

		if (!ctx.bindService(i, con, Context.BIND_AUTO_CREATE)) {
			resultHandler.accept(null, new IllegalStateException("Failed to bind to FermataMediaService"));
		}
	}

	@NonNull
	public MediaSessionCallback getMediaSessionCallback() {
		return sessionCallback;
	}

	@NonNull
	public MediaControllerCompat getMediaController() {
		return mediaController;
	}

	@NonNull
	public MediaLib getLib() {
		return getMediaSessionCallback().getMediaLib();
	}

	public boolean isPlaying() {
		PlaybackStateCompat st = mediaController.getPlaybackState();
		return (st != null) && (st.getState() == PlaybackStateCompat.STATE_PLAYING);
	}

	@Nullable
	public PlayableItem getCurrentItem() {
		return currentItem;
	}

	@Nullable
	public MediaEngine getCurrentEngine() {
		return getMediaSessionCallback().getCurrentEngine();
	}

	public void playItem(PlayableItem i) {
		playItem(i, -1);
	}

	public void playItem(PlayableItem i, long pos) {
		MediaControllerCompat mediaController = getMediaController();

		if (i.equals(getCurrentItem()) && (pos <= 0)) {
			PlaybackStateCompat st = mediaController.getPlaybackState();
			if ((st != null) && (st.getState() == PlaybackStateCompat.STATE_PAUSED)) {
				mediaController.getTransportControls().play();
			}
		} else if (pos > 0) {
			Bundle b = new Bundle();
			b.putLong(MediaSessionCallback.EXTRA_POS, pos);
			mediaController.getTransportControls().playFromMediaId(i.getId(), b);
		} else {
			mediaController.getTransportControls().playFromMediaId(i.getId(), null);
		}
	}

	public void bindPlayPauseButton(View v) {
		playPauseButton = v;
		v.setOnClickListener(b -> onPlayPauseButtonClick());
		v.setOnLongClickListener(this::onPlayPauseButtonLongClick);
	}

	public void onPlayPauseButtonClick() {
		if (isPlaying()) getMediaSessionCallback().onPause();
		else getMediaSessionCallback().onPlay();
	}

	private boolean onPlayPauseButtonLongClick(View v) {
		if (getMediaSessionCallback().getPlaybackControlPrefs().getPlayPauseStopPref()) {
			mediaController.getTransportControls().stop();
		} else if (isPlaying()) {
			mediaController.getTransportControls().pause();
		} else {
			mediaController.getTransportControls().play();
		}
		return true;
	}

	public void bindPrevButton(View v) {
		prevButton = v;
		v.setOnClickListener(this::onPrevButtonClick);
		v.setOnLongClickListener(this::onPrevNextButtonLongClick);
	}

	private void onPrevButtonClick(View v) {
		mediaController.getTransportControls().skipToPrevious();
	}

	public void bindNextButton(View v) {
		nextButton = v;
		v.setOnClickListener(this::onNextButtonClick);
		v.setOnLongClickListener(this::onPrevNextButtonLongClick);
	}

	private void onNextButtonClick(View v) {
		mediaController.getTransportControls().skipToNext();
	}

	public void bindRwButton(View v) {
		rwButton = v;
		v.setOnClickListener(this::onRwFfButtonClick);
		v.setOnLongClickListener(this::onRwFfButtonLongClick);
	}

	public void bindFfButton(View v) {
		ffButton = v;
		v.setOnClickListener(this::onRwFfButtonClick);
		v.setOnLongClickListener(this::onRwFfButtonLongClick);
	}

	private void onRwFfButtonClick(View v) {
		onRwFfButtonClick(v == ffButton);
	}

	public void onRwFfButtonClick(boolean ff) {
		if (ff) {
			mediaController.getTransportControls().fastForward();
		} else {
			mediaController.getTransportControls().rewind();
		}
	}

	private boolean onRwFfButtonLongClick(View v) {
		onRwFfButtonLongClick(v == ffButton);
		return true;
	}

	public void onRwFfButtonLongClick(boolean ff) {
		MediaSessionCallback cb = getMediaSessionCallback();
		PlaybackControlPrefs pp = cb.getPlaybackControlPrefs();
		cb.rewindFastForward(ff, pp.getRwFfLongTimePref(), pp.getRwFfLongTimeUnitPref(), 1);
	}

	private boolean onPrevNextButtonLongClick(View v) {
		MediaSessionCallback cb = getMediaSessionCallback();
		PlaybackControlPrefs pp = cb.getPlaybackControlPrefs();
		cb.rewindFastForward(v == nextButton, pp.getPrevNextLongTimePref(),
				pp.getPrevNextLongTimeUnitPref(), 1);
		return true;
	}

	public void bindProgressBar(SeekBar progressBar) {
		this.progressBar = progressBar;
		progressBar.setEnabled(false);
		progressBar.setOnSeekBarChangeListener(this);
	}

	public void bindProgressTime(TextView progressTime) {
		this.progressTime = progressTime;
		progressTime.setVisibility(INVISIBLE);
	}

	public void bindProgressTotal(TextView progressTotal) {
		this.progressTotal = progressTotal;
		progressTotal.setVisibility(INVISIBLE);
	}

	public void bindControlPanel(View controlPanel) {
		this.controlPanel = controlPanel;
	}

	public void bound() {
		bound = true;
		callback.onPlaybackStateChanged(mediaController.getPlaybackState());
	}

	public void unbind() {
		bound = false;
		currentItem = null;
		callback.stopProgressUpdate();
		if (progressBar != null) progressBar.setOnSeekBarChangeListener(null);
		unbindButtons(playPauseButton, prevButton, nextButton, rwButton, ffButton);
		playPauseButton = prevButton = nextButton = rwButton = ffButton = null;
		controlPanel = null;
	}

	private void unbindButtons(View... buttons) {
		for (View b : buttons) {
			if (b == null) continue;
			b.setOnClickListener(null);
			b.setOnLongClickListener(null);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			callback.setProgressTime(progress);
			mediaController.getTransportControls().seekTo(progress * 1000);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		callback.pauseProgressUpdate(true);
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		callback.pauseProgressUpdate(false);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		BiConsumer<FermataServiceUiBinder, Throwable> rh = requireNonNull(resultHandler);

		try {
			ServiceBinder binder = (ServiceBinder) service;
			sessionCallback = binder.getMediaSessionCallback();
			MediaSessionCompat session = sessionCallback.getSession();
			mediaController = new MediaControllerCompat(ctx, session.getSessionToken());
			callback = new MediaControllerCallback(sessionCallback);
			mediaController.registerCallback(callback);

			resultHandler = null;
			rh.accept(this, null);
			callback.onPlaybackStateChanged(mediaController.getPlaybackState());
		} catch (RemoteException ex) {
			rh.accept(null, ex);
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		if (isConnected()) {
			callback.stopProgressUpdate();
			mediaController.unregisterCallback(callback);
		}
	}

	public boolean isConnected() {
		return resultHandler == null;
	}

	private final class MediaControllerCallback extends MediaControllerCompat.Callback {
		private final MediaSessionCallback sessionCallback;
		private final Handler handler = FermataApplication.get().getHandler();
		private final StringBuilder timeBuilder = new StringBuilder(10);
		private Object progressUpdateStamp;
		boolean pauseProgressUpdate;
		boolean updateDuration;

		public MediaControllerCallback(MediaSessionCallback sessionCallback) {
			this.sessionCallback = sessionCallback;
		}

		@Override
		public void onPlaybackStateChanged(PlaybackStateCompat state) {
			if ((state == null) || !bound) return;

			int st = state.getState();
			boolean fireEvent = true;

			switch (st) {
				case PlaybackStateCompat.STATE_PAUSED:
				case PlaybackStateCompat.STATE_PLAYING:
					playPause(st);
					break;
				case PlaybackStateCompat.STATE_ERROR:
					Toast.makeText(ctx, state.getErrorMessage(), Toast.LENGTH_LONG).show();
				case PlaybackStateCompat.STATE_NONE:
				case PlaybackStateCompat.STATE_STOPPED:
					resetProgressBar();
					showPanel(false);

					break;
				case PlaybackStateCompat.STATE_FAST_FORWARDING:
				case PlaybackStateCompat.STATE_REWINDING:
				case PlaybackStateCompat.STATE_BUFFERING: // TODO: implement
				case PlaybackStateCompat.STATE_CONNECTING: // TODO: implement
				case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
				case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
				case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
					showPanel(true);
					fireEvent = false;
					break;
			}

			if (fireEvent) {
				PlayableItem i = sessionCallback.getCurrentItem();

				if (!Objects.equals(currentItem, i)) {
					PlayableItem old = currentItem;
					currentItem = i;
					fireBroadcastEvent(l -> l.onPlayableChanged(old, i));
				}
			}
		}

		void pauseProgressUpdate(boolean pause) {
			pauseProgressUpdate = pause;
		}

		void setProgressTime(int seconds) {
			if (progressTime != null) progressTime.setText(timeToString(seconds));
		}

		private void startProgressUpdate() {
			if ((progressUpdateStamp == null) && ((progressBar != null) || (progressTime != null))) {
				progressUpdateStamp = new Object();
				handler.postDelayed(() -> updateProgress(progressUpdateStamp), 1000);
			}
		}

		private void stopProgressUpdate() {
			progressUpdateStamp = null;
		}

		private void updateProgress(Object stamp) {
			if (progressUpdateStamp != stamp) return;

			if (!pauseProgressUpdate) {
				MediaEngine eng = sessionCallback.getCurrentEngine();

				if ((eng != null) && (eng.getSource() != null)) {
					int pos = (int) (eng.getPosition() / 1000);
					if (progressBar != null) {
						progressBar.setProgress(pos);

						if (updateDuration) {
							long dur = eng.getDuration();

							if (dur > 0) {
								updateDuration = false;
								int max = (int) (dur / 1000);
								PlayableItem i = eng.getSource();
								i.setDuration(dur);
								progressBar.setMax(max);
								if (progressTotal != null) progressTotal.setText(timeToString(max));
								fireBroadcastEvent(l -> l.durationChanged(i));
							}
						}
					}

					setProgressTime(pos);
				}
			}

			handler.postDelayed(() -> updateProgress(stamp), 1000);
		}

		private StringBuilder timeToString(int seconds) {
			timeBuilder.setLength(0);
			TextUtils.timeToString(timeBuilder, seconds);
			return timeBuilder;
		}

		private void playPause(int st) {
			MediaEngine eng = sessionCallback.getCurrentEngine();

			if (eng != null) {
				int dur = (int) (eng.getSource().getDuration() / 1000);
				int pos = (int) (eng.getPosition() / 1000);

				if (progressBar != null) {
					progressBar.setEnabled(true);
					progressBar.setMax(dur);
					progressBar.setProgress(pos);
				}
				if (progressTime != null) {
					progressTime.setVisibility(VISIBLE);
					progressTime.setText(timeToString(pos));
				}
				if (progressTotal != null) {
					progressTotal.setVisibility(VISIBLE);
					progressTotal.setText(timeToString(dur));
				}

				if (st == STATE_PLAYING) {
					updateDuration = (dur <= 0);
					startProgressUpdate();
					if (playPauseButton != null) playPauseButton.setSelected(true);
				} else {
					stopProgressUpdate();
					if (playPauseButton != null) playPauseButton.setSelected(false);
				}

				showPanel(true);
			} else {
				resetProgressBar();
			}
		}

		private void showPanel(boolean show) {
			if (controlPanel != null) controlPanel.setVisibility(show ? VISIBLE : GONE);
		}

		private void resetProgressBar() {
			if (progressTime != null) progressTime.setVisibility(INVISIBLE);
			if (progressTotal != null) progressTotal.setVisibility(INVISIBLE);

			if (progressBar != null) {
				progressBar.setProgress(0);
				progressBar.setEnabled(false);
			}

			stopProgressUpdate();
		}
	}

	public interface Listener {

		void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem);

		default void durationChanged(PlayableItem i) {
		}
	}
}
