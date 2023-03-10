package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class FermataServiceUiBinder extends BasicEventBroadcaster<FermataServiceUiBinder.Listener>
		implements OnSeekBarChangeListener {
	private final MediaSessionCallback sessionCallback;
	private final MediaControllerCallback callback;
	private final MediaControllerCompat mediaController;
	private PlayableItem currentItem;
	private boolean bound;
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

	FermataServiceUiBinder(FermataMediaServiceConnection c) {
		sessionCallback = c.getMediaSessionCallback();
		MediaSessionCompat session = sessionCallback.getSession();
		mediaController = new MediaControllerCompat(App.get(),
				session.getSessionToken());
		callback = new MediaControllerCallback();
	}

	@NonNull
	public MediaSessionCallback getMediaSessionCallback() {
		return sessionCallback;
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
		return getMediaSessionCallback().getEngine();
	}

	public void playItem(PlayableItem i) {
		playItem(i, -1);
	}

	public void playItem(PlayableItem i, long pos) {
		if (i.equals(getCurrentItem()) && (pos <= 0)) {
			if (sessionCallback.getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED) {
				sessionCallback.onPlay();
			}
		} else {
			sessionCallback.playItem(i, pos);
		}
	}

	public void stop() {
		mediaController.getTransportControls().stop();
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
		v.setOnClickListener(this::onPrevNextButtonClick);
		v.setOnLongClickListener(this::onPrevNextButtonLongClick);
	}

	public void bindNextButton(View v) {
		nextButton = v;
		v.setOnClickListener(this::onPrevNextButtonClick);
		v.setOnLongClickListener(this::onPrevNextButtonLongClick);
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

	private void onPrevNextButtonClick(View v) {
		onPrevNextButtonClick(v == nextButton);
	}

	public void onPrevNextButtonClick(boolean next) {
		if (next) mediaController.getTransportControls().skipToNext();
		else mediaController.getTransportControls().skipToPrevious();
	}

	private boolean onPrevNextButtonLongClick(View v) {
		onPrevNextButtonLongClick(v == nextButton);
		return true;
	}

	public void onPrevNextButtonLongClick(boolean next) {
		MediaSessionCallback cb = getMediaSessionCallback();
		PlaybackControlPrefs pp = cb.getPlaybackControlPrefs();
		cb.rewindFastForward(next, pp.getPrevNextLongTimePref(),
				pp.getPrevNextLongTimeUnitPref(), 1);
	}

	public void onPrevNextFolderClick(boolean next) {
		PlayableItem i = sessionCallback.getCurrentItem();
		if (i == null) return;
		MediaLib.BrowsableItem p = i.getParent();
		FutureSupplier<PlayableItem> f = next ? p.getNextPlayable() : p.getPrevPlayable();
		f.onSuccess(pi -> {
			if (pi != null) sessionCallback.playItem(pi, 0);
		});
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

	public void bindProgressBar(SeekBar progressBar) {
		this.progressBar = progressBar;
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
		assert !bound;
		bound = true;
		mediaController.registerCallback(callback);
		callback.onPlaybackStateChanged(mediaController.getPlaybackState());
		Log.d("UI bound");
	}

	public void unbind() {
		assert bound;
		bound = false;
		callback.stopProgressUpdate();
		mediaController.unregisterCallback(callback);
		currentItem = null;
		callback.stopProgressUpdate();
		if (progressBar != null) progressBar.setOnSeekBarChangeListener(null);
		unbindButtons(playPauseButton, prevButton, nextButton, rwButton, ffButton);
		playPauseButton = prevButton = nextButton = rwButton = ffButton = null;
		progressTime = progressTotal = null;
		progressBar = null;
		controlPanel = null;
		Log.d("UI unbound");
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
			mediaController.getTransportControls().seekTo(progress * 1000L);
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

	private final class MediaControllerCallback extends MediaControllerCompat.Callback {
		private final Handler handler = FermataApplication.get().getHandler();
		private final StringBuilder timeBuilder = new StringBuilder(10);
		private Object progressUpdateStamp;
		boolean pauseProgressUpdate;
		boolean updateDuration;
		FutureSupplier<Long> duration;

		MediaControllerCallback() {
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
					String err = state.getErrorMessage().toString();
					fireBroadcastEvent(l -> l.onPlaybackError(err));
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
				MediaEngine eng = sessionCallback.getEngine();
				PlayableItem src = (eng != null) ? eng.getSource() : null;

				if (src != null) {
					eng.getPosition().main().onSuccess(position -> {
						if (eng != sessionCallback.getEngine()) return;

						int pos = (int) (position / 1000);
						if (progressBar != null) {
							progressBar.setProgress(pos);

							if (updateDuration) {
								eng.getDuration().main().onSuccess(dur -> {
									if (dur > 0) {
										updateDuration = false;
										int max = (int) (dur / 1000);
										long last = getLib().getLastPlayedPosition(src);
										src.setDuration(dur);
										if (last > 0) eng.setPosition(last);
										progressBar.setMax(max);
										if (progressTotal != null) progressTotal.setText(timeToString(max));
										fireBroadcastEvent(l -> l.onDurationChanged(src));
									}
								});
							}

							if (src instanceof StreamItem) {
								FutureSupplier<Long> dur = eng.getDuration();

								if (dur != duration) {
									duration = dur;
									dur.onSuccess(d -> {
										int max = (int) (d / 1000);
										progressBar.setMax(max);
										if (progressTotal != null) progressTotal.setText(timeToString(max));
									});
								}

								src.getMediaDescription().onSuccess(md -> {
									Bundle b = md.getExtras();
									if (b != null) {
										long start = b.getLong(STREAM_START_TIME, 0);
										long end = b.getLong(STREAM_END_TIME, 0);

										if (start < end) {
											int second = (int) ((System.currentTimeMillis() - start) / 1000);
											progressBar.setSecondaryProgress((second >= progressBar.getProgress()) ?
													second : progressBar.getMax());
											return;
										}
									}
									progressBar.setSecondaryProgress(0);
								});
							}
						}

						setProgressTime(pos);
					});
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
			PlayableItem i;
			MediaEngine eng = sessionCallback.getEngine();

			if ((eng != null) && ((i = eng.getSource()) != null)) {
				FutureSupplier<Long> getPos = eng.getPosition().main();
				duration = i.isStream() ? eng.getDuration() : i.getDuration();
				duration.main().onCompletion((dur, fail) -> {
					if (fail != null) {
						Log.d(fail);
						resetProgressBar();
					} else {
						getPos.onCompletion((pos, f) -> {
							if (f != null) {
								Log.d(f);
								resetProgressBar();
							} else if ((sessionCallback.getEngine() == eng) && (i == eng.getSource())) {
								playPause(eng, st, (int) (dur / 1000), (int) (pos / 1000));
							} else {
								resetProgressBar();
							}
						});
					}
				});
			} else {
				resetProgressBar();
			}
		}

		private void playPause(MediaEngine eng, int st, int dur, int pos) {
			boolean canSeek = eng.canSeek();

			if (canSeek) {
				if (progressBar != null) {
					progressBar.setEnabled(true);
					progressBar.setVisibility(VISIBLE);
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
				if (rwButton != null) rwButton.setVisibility(VISIBLE);
				if (ffButton != null) ffButton.setVisibility(VISIBLE);
			} else {
				if (progressBar != null) {
					progressBar.setEnabled(false);
					progressBar.setVisibility(GONE);
				}
				if (progressTime != null) progressTime.setVisibility(GONE);
				if (progressTotal != null) progressTotal.setVisibility(GONE);
				if (rwButton != null) rwButton.setVisibility(GONE);
				if (ffButton != null) ffButton.setVisibility(GONE);
			}

			if (st == STATE_PLAYING) {
				updateDuration = (dur <= 0);
				if (canSeek) startProgressUpdate();
				else stopProgressUpdate();

				if (playPauseButton != null) {
					if (eng.canPause()) {
						playPauseButton.setSelected(false);
						playPauseButton.setActivated(true);
					} else {
						playPauseButton.setSelected(false);
						playPauseButton.setActivated(false);
					}
				}
			} else {
				stopProgressUpdate();

				if (playPauseButton != null) {
					playPauseButton.setSelected(true);
					playPauseButton.setActivated(false);
				}
			}

			showPanel(true);
		}

		private void showPanel(boolean show) {
			if (controlPanel != null) controlPanel.setVisibility(show ? VISIBLE : GONE);
		}

		private void resetProgressBar() {
			if (progressTime != null) progressTime.setVisibility(INVISIBLE);
			if (progressTotal != null) progressTotal.setVisibility(INVISIBLE);
			if (progressBar != null) {
				progressBar.setProgress(0);
				progressBar.setSecondaryProgress(0);
			}
			stopProgressUpdate();
		}
	}

	public interface Listener {

		void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem);

		default void onPlaybackError(String message) {
		}

		default void onDurationChanged(PlayableItem i) {
		}
	}
}
