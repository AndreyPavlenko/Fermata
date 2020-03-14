package me.aap.fermata.media.service;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.pref.PreferenceStore;

import static android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_FAST_FORWARD;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_URI;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_REWIND;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_GROUP;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_INVALID;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_INVALID;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_FAST_FORWARDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_REWINDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static me.aap.fermata.media.pref.MediaPrefs.AE_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.BASS_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.BASS_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_BANDS;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_PRESET;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_USER_PRESETS;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_MODE;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.getUserPresetBands;
import static me.aap.fermata.media.pref.PlaybackControlPrefs.getTimeMillis;

/**
 * @author Andrey Pavlenko
 */
public class MediaSessionCallback extends MediaSessionCompat.Callback implements SharedConstants,
		MediaEngine.Listener, AudioManager.OnAudioFocusChangeListener,
		EventBroadcaster<MediaSessionCallback.Listener>, Closeable {
	public static final String EXTRA_POS = "me.aap.fermata.extra.pos";
	private static final String TAG = "MediaSessionCallback";
	private static final long SUPPORTED_ACTIONS = ACTION_PLAY | ACTION_STOP | ACTION_PAUSE | ACTION_PLAY_PAUSE
			| ACTION_PLAY_FROM_MEDIA_ID | ACTION_PLAY_FROM_SEARCH | ACTION_PLAY_FROM_URI
			| ACTION_SKIP_TO_PREVIOUS | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_QUEUE_ITEM
			| ACTION_REWIND | ACTION_FAST_FORWARD | ACTION_SEEK_TO
			| ACTION_SET_REPEAT_MODE | ACTION_SET_SHUFFLE_MODE;
	private final Collection<ListenerRef<MediaSessionCallback.Listener>> listeners = new LinkedList<>();
	private final MediaLib lib;
	private final FermataMediaService service;
	private final MediaSessionCompat session;
	private final MediaEngineManager engineManager;
	private final PlaybackControlPrefs playbackControlPrefs;
	private final Handler handler;
	private final AudioManager audioManager;
	private final AudioFocusRequestCompat audioFocusReq;
	private final PlaybackStateCompat.CustomAction customRewind;
	private final PlaybackStateCompat.CustomAction customFastForward;
	private final PlaybackStateCompat.CustomAction customRepeatEnable;
	private final PlaybackStateCompat.CustomAction customRepeatDisable;
	private final PlaybackStateCompat.CustomAction customFavoritesAdd;
	private final PlaybackStateCompat.CustomAction customFavoritesRemove;
	private final BroadcastReceiver onNoisy;
	private MediaEngine engine;
	private long keyPressTime;
	private int preBufferingState;
	private boolean playOnPrepared;
	private boolean playOnAudioFocus;
	private boolean tryAnotherEngine;
	@NonNull
	private PlaybackStateCompat currentState;
	private List<VideoViewWraper> videoView;

	public MediaSessionCallback(FermataMediaService service, MediaSessionCompat session, MediaLib lib,
															PlaybackControlPrefs playbackControlPrefs, Handler handler) {
		this.lib = lib;
		this.service = service;
		this.session = session;
		this.engineManager = new MediaEngineManager(lib);
		this.playbackControlPrefs = playbackControlPrefs;
		this.handler = handler;
		Context ctx = lib.getContext();


		customRewind = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_RW,
				ctx.getString(R.string.rewind), R.drawable.rw).build();
		customFastForward = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FF,
				ctx.getString(R.string.fast_forward), R.drawable.ff).build();
		customRepeatEnable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_REPEAT_ENABLE,
				ctx.getString(R.string.repeat), R.drawable.repeat).build();
		customRepeatDisable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_REPEAT_DISABLE,
				ctx.getString(R.string.repeat_disable), R.drawable.repeat_filled).build();
		customFavoritesAdd = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FAVORITES_ADD,
				ctx.getString(R.string.favorites_add), R.drawable.favorite).build();
		customFavoritesRemove = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FAVORITES_REMOVE,
				ctx.getString(R.string.favorites_remove), R.drawable.favorite_filled).build();

		currentState = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS).build();
		setPlaybackState(currentState);
		session.setActive(true);

		audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

		if (audioManager != null) {
			AudioAttributesCompat focusAttrs = new AudioAttributesCompat.Builder()
					.setUsage(AudioAttributesCompat.USAGE_MEDIA)
					.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
					.build();
			audioFocusReq = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
					.setAudioAttributes(focusAttrs)
					.setWillPauseWhenDucked(false)
					.setOnAudioFocusChangeListener(this)
					.build();
		} else {
			audioFocusReq = null;
		}

		onNoisy = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
					onPause();
				}
			}
		};
		ctx.registerReceiver(onNoisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
	}

	public MediaLib getMediaLib() {
		return lib;
	}

	public MediaEngine getCurrentEngine() {
		return engine;
	}

	public PlayableItem getCurrentItem() {
		return (engine == null) ? null : engine.getSource();
	}

	public MediaSessionCompat getSession() {
		return session;
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return playbackControlPrefs;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners;
	}

	public MediaEngine getEngine() {
		return engine;
	}

	public MediaEngineManager getEngineManager() {
		return engineManager;
	}

	public void addVideoView(VideoView view, int priority) {
		if (this.videoView == null) {
			videoView = new ArrayList<>(2);
			videoView.add(new VideoViewWraper(view, priority));
		} else {
			for (VideoViewWraper s : videoView) {
				if (s.view == view) return;
			}

			videoView.add(new VideoViewWraper(view, priority));
			Collections.sort(videoView);
		}

		if (engine != null) {
			PlayableItem i = engine.getSource();
			if (i.isVideo()) engine.setVideoView(videoView.get(0).view);
		}
	}

	public void removeVideoView(VideoView view) {
		if ((videoView != null) && videoView.remove(new VideoViewWraper(view, 0))) {
			if (videoView.isEmpty()) {
				videoView = null;
				if (engine != null) engine.setVideoView(null);
			} else if (engine != null) {
				engine.setVideoView(videoView.get(0).view);
			}
		}
	}

	public VideoView getVideoView() {
		return (videoView == null) ? null : videoView.get(0).view;
	}

	@Override
	public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
		KeyEvent ke = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
		if ((ke != null) && onMediaButtonEvent(ke)) return true;
		else return super.onMediaButtonEvent(mediaButtonEvent);
	}

	public boolean onMediaButtonEvent(KeyEvent ke) {
		if (ke.getAction() == KeyEvent.ACTION_DOWN) {
			switch (ke.getKeyCode()) {
				case KeyEvent.KEYCODE_MEDIA_NEXT:
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
					long time = keyPressTime = System.currentTimeMillis();
					boolean ff = ke.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT;
					handler.postDelayed(() -> delayedNextPrev(time, ff), 1000);
					return true;
			}
		} else if (ke.getAction() == KeyEvent.ACTION_UP) {
			switch (ke.getKeyCode()) {
				case KeyEvent.KEYCODE_MEDIA_NEXT:
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
					long holdTime = System.currentTimeMillis() - keyPressTime;
					keyPressTime = 0;

					if (holdTime <= 1000) {
						if (ke.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT) {
							onSkipToNext();
						} else {
							onSkipToPrevious();
						}
					}

					return true;
			}
		}

		return false;
	}

	public void close() {
		onStop();
		session.setActive(false);
		lib.getContext().unregisterReceiver(onNoisy);
		listeners.clear();
		engineManager.close();
	}

	@Override
	public void onPrepare() {
		int st = getPlaybackState().getState();
		if ((st != PlaybackState.STATE_NONE) && (st != PlaybackState.STATE_ERROR)) return;

		PlayableItem i = lib.getLastPlayedItem();
		if ((i == null) || i.isVideo()) return;

		engine = engineManager.createEngine(engine, i, this);
		Log.i(getClass().getName(), "MediaEngine " + engine + " created for " + i);
		if (engine == null) return;

		playOnPrepared = false;
		if (i.isVideo() && (videoView != null))
			engine.setVideoView(videoView.get(0).view);
		tryAnotherEngine = true;
		engine.prepare(i);
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void onPlay() {
		if (!requestAudioFocus()) {
			Log.i(getClass().getName(), "Audio focus request failed");
			return;
		}

		PlaybackStateCompat state = getPlaybackState();
		PlayableItem i;

		switch (state.getState()) {
			case PlaybackStateCompat.STATE_NONE:
			case PlaybackStateCompat.STATE_ERROR:
				i = lib.getLastPlayedItem();
				if (i != null) playItem(i, lib.getLastPlayedPosition(i));
				break;
			case PlaybackStateCompat.STATE_PAUSED:
				assert (engine != null);
				assert (engine.getSource() != null);
				long pos = state.getPosition();
				float speed = getSpeed(engine.getSource());
				state = new PlaybackStateCompat.Builder(state)
						.setState(PlaybackStateCompat.STATE_PLAYING, pos, speed).build();
				setPlaybackState(state);
				engine.setPosition(pos);
				start(engine, speed);
				break;
			default:
				break;
		}
	}


	@Override
	public void onPlayFromMediaId(String mediaId, Bundle extras) {
		if (!requestAudioFocus()) {
			Log.i(getClass().getName(), "Audio focus request failed");
			return;
		}

		Item i = lib.getItem(mediaId);
		PlayableItem pi = null;

		if (i instanceof PlayableItem) {
			pi = (PlayableItem) i;
		} else if (i instanceof BrowsableItem) {
			pi = ((BrowsableItem) i).getFirstPlayable();
		}

		if (pi != null) {
			long pos = (extras == null) ? 0 : extras.getLong(EXTRA_POS, 0);
			playItem(pi, pos);
		} else {
			String msg = lib.getContext().getResources().getString(R.string.err_failed_to_play, mediaId);
			Log.w(TAG, msg);
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
					.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
			setPlaybackState(state);
		}
	}

	@Override
	public void onPlayFromSearch(String query, Bundle extras) {
		if (TextUtils.isEmpty(query)) {
			onPlay();
		}
		//TODO: implement
	}

	@Override
	public void onPause() {
		PlayableItem i = getCurrentItem();

		if (i != null) {
			engine.pause();
			long pos = engine.getPosition();
			lib.setLastPlayed(i, pos);
			PlaybackStateCompat.Builder state = createPlayingState(i, true, pos, engine.getSpeed());
			setPlaybackState(state.build());
		}
	}

	@Override
	public void onStop() {
		onStop(true);
	}

	private void onStop(boolean setPosition) {
		if (getPlaybackState().getState() != PlaybackStateCompat.STATE_STOPPED) {
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f).build();
			setPlaybackState(state, null, Collections.emptyList(), REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID);
		}

		if (engine != null) {
			if (setPosition) {
				PlayableItem i = engine.getSource();
				if (i != null) lib.setLastPlayed(i, engine.getPosition());
			}

			engine.stop();
			engine.close();
			engine = null;
		}

		releaseAudioFocus();
		session.setQueue(null);
		session.setActive(false);
	}

	@Override
	public void onSeekTo(long position) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		PlaybackStateCompat state = getPlaybackState();
		engine.setPosition(position);
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(state.getState(), engine.getPosition(), engine.getSpeed());
		setPlaybackState(b.build());
	}

	@Override
	public void onSkipToPrevious() {
		skipTo(false);
	}

	@Override
	public void onSkipToNext() {
		skipTo(true);
	}

	private void skipTo(boolean next) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		i = next ? i.getNextPlayable() : i.getPrevPlayable();
		if (i != null) skipTo(next, i);
	}

	private void skipTo(boolean next, PlayableItem i) {
		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(next ? STATE_SKIPPING_TO_NEXT : STATE_SKIPPING_TO_PREVIOUS, state.getPosition(),
				state.getPlaybackSpeed());
		setPlaybackState(b.build());
		playItem(i, 0);
	}

	private void delayedNextPrev(long time, boolean ff) {
		if (keyPressTime != time) return;
		long holdTime = System.currentTimeMillis() - keyPressTime;
		onRwFf(ff, (int) (holdTime / 1000));
		handler.postDelayed(() -> delayedNextPrev(time, ff), 1000);
	}

	@Override
	public void onRewind() {
		onRwFf(false, 1);
	}

	@Override
	public void onFastForward() {
		onRwFf(true, 1);
	}

	private void onRwFf(boolean ff, int multiply) {
		PlaybackControlPrefs pp = getPlaybackControlPrefs();
		rewindFastForward(ff, pp.getRwFfTimePref(), pp.getRwFfTimeUnitPref(), multiply);
	}

	public void rewindFastForward(boolean ff, int time, int timeUnit, int multiply) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(ff ? STATE_FAST_FORWARDING : STATE_REWINDING, state.getPosition(),
				state.getPlaybackSpeed());
		setPlaybackState(b.build());

		long dur = i.getDuration();
		long timeShift = getTimeMillis(dur, time, timeUnit) * multiply;

		if (ff) {
			dur -= 1000;
			if (dur <= 0) return;
			long pos = engine.getPosition() + timeShift;
			engine.setPosition(Math.min(pos, dur));
		} else {
			long pos = engine.getPosition() - timeShift;
			engine.setPosition(pos > 0 ? pos : 0);
		}

		setPlaybackState(b.setState(state.getState(), engine.getPosition(), engine.getSpeed()).build());
	}

	@Override
	public void onCustomAction(String action, Bundle extras) {
		switch (action) {
			case CUSTOM_ACTION_RW:
				onRewind();
				break;
			case CUSTOM_ACTION_FF:
				onFastForward();
				break;
			case CUSTOM_ACTION_REPEAT_ENABLE:
				repeatEnableDisable(true);
				break;
			case CUSTOM_ACTION_REPEAT_DISABLE:
				repeatEnableDisable(false);
				break;
			case CUSTOM_ACTION_FAVORITES_ADD:
				favoriteAddRemove(true);
				break;
			case CUSTOM_ACTION_FAVORITES_REMOVE:
				favoriteAddRemove(false);
				break;
		}
	}

	private void repeatEnableDisable(boolean enable) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		PlaybackStateCompat state = getPlaybackState();
		List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();
		i.getParent().getPrefs().setRepeatPref(enable);

		if (enable) {
			CollectionUtils.replace(actions, customRepeatEnable, customRepeatDisable);
		} else {
			CollectionUtils.replace(actions, customRepeatDisable, customRepeatEnable);
		}

		setPlaybackState(new PlaybackStateCompat.Builder(state).build());
	}

	void favoriteAddRemove(boolean add) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		Favorites favorites = lib.getFavorites();
		PlaybackStateCompat state = getPlaybackState();
		List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();

		if (add) CollectionUtils.replace(actions, customFavoritesAdd, customFavoritesRemove);
		else CollectionUtils.replace(actions, customFavoritesRemove, customFavoritesAdd);

		if (add) favorites.addItem(i);
		else favorites.removeItem(i);

		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		List<MediaSessionCompat.QueueItem> queue = null;

		if (i.getParent() == favorites) {
			session.setQueue(queue = favorites.getQueue());
			b.setActiveQueueItemId(i.getQueueId()).build();
		}

		setPlaybackState(b.build(), queue);
	}

	@Override
	public void onSkipToQueueItem(long queueId) {
		PlayableItem pi = getCurrentItem();
		if (pi == null) return;

		List<? extends Item> children = pi.getParent().getChildren();
		if ((queueId < 0) || (queueId >= children.size())) return;

		Item i = children.get((int) queueId);

		if (i instanceof PlayableItem) {
			pi = (PlayableItem) i;
		} else if (i instanceof BrowsableItem) {
			pi = ((BrowsableItem) i).getFirstPlayable();
		} else {
			return;
		}

		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM, state.getPosition(),
				state.getPlaybackSpeed());
		setPlaybackState(b.build());
		playItem(pi, 0);
	}

	@Override
	public void onSetShuffleMode(int shuffleMode) {
		PlayableItem i = getCurrentItem();
		if (i != null) i.getParent().getPrefs().setShufflePref(shuffleMode != SHUFFLE_MODE_NONE);
	}

	@Override
	public void onSetRepeatMode(int repeatMode) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		BrowsableItemPrefs p = i.getParent().getPrefs();

		switch (repeatMode) {
			case PlaybackStateCompat.REPEAT_MODE_INVALID:
			case REPEAT_MODE_NONE:
				p.setRepeatItemPref(null);
				p.setRepeatPref(false);
				break;
			case REPEAT_MODE_ONE:
				p.setRepeatItemPref(i.getId());
				p.setRepeatPref(false);
				break;
			case REPEAT_MODE_ALL:
			case REPEAT_MODE_GROUP:
				p.setRepeatItemPref(null);
				p.setRepeatPref(true);
				break;
		}
	}

	@Override
	public void onSetPlaybackSpeed(float speed) {
		if (engine != null) engine.setSpeed(speed);
	}

	@Override
	public void onEngineBuffering(MediaEngine engine, int percent) {
		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state)
				.setState(PlaybackStateCompat.STATE_BUFFERING, engine.getPosition(), engine.getSpeed());
		setPlaybackState(b.build());
		if (preBufferingState == -1) preBufferingState = state.getState();
	}

	@Override
	public void onEngineBufferingCompleted(MediaEngine engine) {
		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state)
				.setState(preBufferingState, engine.getPosition(), engine.getSpeed());
		setPlaybackState(b.build());
		preBufferingState = -1;
	}

	@Override
	public void onEnginePrepared(MediaEngine engine) {
		PlayableItem i = engine.getSource();
		if (i == null) return;

		long pos = lib.getLastPlayedPosition(i);
		long dur = engine.getDuration();
		float speed = getSpeed(i);
		engine.setPosition((pos > dur) ? 0 : pos);

		BrowsableItemPrefs p = i.getParent().getPrefs();
		int shuffle = p.getShufflePref() ? SHUFFLE_MODE_ALL : SHUFFLE_MODE_NONE;
		int repeat;

		if (p.getRepeatPref()) {
			repeat = REPEAT_MODE_ALL;
		} else if (i.getId().equals(p.getRepeatItemPref())) {
			repeat = REPEAT_MODE_ONE;
		} else {
			repeat = REPEAT_MODE_NONE;
		}

		MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder(i.getMediaData());
		mb.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, i.getTitle());
		mb.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, i.getSubtitle());
		MediaMetadataCompat meta = mb.build();

		session.setMetadata(meta);
		session.setRepeatMode(repeat);
		session.setShuffleMode(shuffle);
		setAudiEffects(engine, i.getPrefs(), i.getParent().getPrefs(), getPlaybackControlPrefs());
		setPlaybackState(createPlayingState(i, !playOnPrepared, pos, speed).build(), meta, null, repeat, shuffle);

		if (playOnPrepared) {
			lib.setLastPlayed(i, pos);
			start(engine, speed);
		}
	}

	@Override
	public void onEngineEnded(MediaEngine engine) {
		PlayableItem i = engine.getSource();

		if (i != null) {
			PlayableItem next = i.getNextPlayable();
			if (i.isVideo()) i.getPrefs().setWatchedPref(true);

			if (next != null) {
				skipTo(true, next);
			} else {
				onStop(false);
				lib.setLastPlayed(i, 0);
			}
		} else {
			onStop(false);
		}
	}

	@Override
	public void onVideoSizeChanged(MediaEngine engine, int width, int height) {
		if (videoView != null) videoView.get(0).view.setSurfaceSize(engine);
	}

	@Override
	public void onEngineError(MediaEngine engine, Throwable ex) {
		String msg;
		PlayableItem i = engine.getSource();

		if (TextUtils.isEmpty(ex.getLocalizedMessage())) {
			msg = lib.getContext().getResources().getString(R.string.err_failed_to_play, i);
		} else {
			msg = lib.getContext().getResources().getString(R.string.err_failed_to_play_cause,
					i, ex.getLocalizedMessage());
		}

		Log.w(TAG, msg, ex);

		if (tryAnotherEngine) {
			this.engine = engineManager.createAnotherEngine(engine, this);

			if (this.engine != null) {
				Log.i(getClass().getName(), "Trying another engine: " + this.engine);
				tryAnotherEngine = false;
				this.engine.prepare(i);
				return;
			}
		}

		PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
				.setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
				.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
		setPlaybackState(state);
		onStop();
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
			case AudioManager.AUDIOFOCUS_GAIN:
				if (playOnAudioFocus) {
					playOnAudioFocus = false;
					onPlay();
				}

				break;
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				// onPause();
				break;
			default:
				if (isPlaying()) {
					playOnAudioFocus = true;
					onPause();
				}

				break;
		}
	}

	private void playItem(PlayableItem i, long pos) {
		PlayableItem current = getCurrentItem();
		long currentPos = (current != null) ? engine.getPosition() : 0;
		engine = engineManager.createEngine(engine, i, this);

		if (engine == null) {
			if (current != null) lib.setLastPlayed(current, currentPos);
			String msg = lib.getContext().getResources().getString(R.string.err_unsupported_source_type, i);
			PlaybackStateCompat state = new PlaybackStateCompat.Builder()
					.setActions(SUPPORTED_ACTIONS)
					.setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
					.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
			setPlaybackState(state, Collections.emptyList());
			return;
		}

		BrowsableItem p = i.getParent();

		if (current != null) {
			if (!p.equals(current.getParent())) {
				lib.setLastPlayed(current, currentPos);
				List<MediaSessionCompat.QueueItem> queue = p.getQueue();
				session.setQueue(queue);
				service.updateSessionState(null, null, queue, REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID);
			} else {
				lib.setLastPlayed(i, pos);
			}
		} else {
			List<MediaSessionCompat.QueueItem> queue = p.getQueue();
			session.setQueue(queue);
			service.updateSessionState(null, null, queue, REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID);
		}

		playOnPrepared = true;
		if (i.isVideo() && (videoView != null))
			engine.setVideoView(videoView.get(0).view);
		tryAnotherEngine = true;
		engine.prepare(i);
	}

	private PlaybackStateCompat.Builder createPlayingState(PlayableItem i, boolean pause,
																												 long position, float speed) {
		int state = pause ? PlaybackStateCompat.STATE_PAUSED : PlaybackStateCompat.STATE_PLAYING;
		BrowsableItemPrefs p = i.getParent().getPrefs();
		boolean repeat = p.getRepeatPref();
		return new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
				.setState(state, position, speed)
				.setActiveQueueItemId(i.getQueueId())
				.addCustomAction(customRewind).addCustomAction(customFastForward)
				.addCustomAction(repeat ? customRepeatDisable : customRepeatEnable)
				.addCustomAction(i.isFavoriteItem() ? customFavoritesRemove : customFavoritesAdd);
	}

	private void setPlaybackState(PlaybackStateCompat state) {
		setPlaybackState(state, null);
	}

	private void setPlaybackState(PlaybackStateCompat state, List<MediaSessionCompat.QueueItem> queue) {
		setPlaybackState(state, null, queue, REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID);
	}

	private void setPlaybackState(PlaybackStateCompat state, MediaMetadataCompat meta,
																List<MediaSessionCompat.QueueItem> queue, int repeat, int shuffle) {
		currentState = state;
		session.setPlaybackState(state);
		service.updateSessionState(state, meta, queue, repeat, shuffle);
		service.updateNotification(state.getState(), getCurrentItem());
		fireBroadcastEvent(l -> l.onPlaybackStateChanged(this, state));

		if (state.getState() == STATE_PLAYING) {
			MediaEngine engine = getEngine();
			PlayableItem i = engine.getSource();
			if (i.isTimerRequired()) startTimer(i, state.getPosition(), state.getPlaybackSpeed());
		} else {
			stopTimer();
		}
	}

	@NonNull
	PlaybackStateCompat getPlaybackState() {
		return currentState;
	}

	public boolean isPlaying() {
		return currentState.getState() == PlaybackStateCompat.STATE_PLAYING;
	}

	private float getSpeed(PlayableItem i) {
		PreferenceStore prefs = i.getPrefs();

		if (prefs.hasPref(MediaPrefs.SPEED)) {
			return prefs.getFloatPref(MediaPrefs.SPEED);
		} else {
			prefs = i.getParent().getPrefs();
			return prefs.hasPref(MediaPrefs.SPEED) ? prefs.getFloatPref(MediaPrefs.SPEED) :
					getPlaybackControlPrefs().getFloatPref(MediaPrefs.SPEED);
		}
	}

	private void start(MediaEngine engine, float speed) {
		engine.setSpeed(speed);
		engine.start();
	}

	private void setAudiEffects(MediaEngine engine, PreferenceStore... stores) {
		AudioEffects ae = engine.getAudioEffects();
		if (ae == null) return;

		Equalizer eq = ae.getEqualizer();
		Virtualizer virt = ae.getVirtualizer();
		BassBoost bass = ae.getBassBoost();

		for (PreferenceStore s : stores) {
			if (!s.getBooleanPref(AE_ENABLED)) continue;

			if (eq != null) {
				if (s.getBooleanPref(EQ_ENABLED)) {
					try {
						short num = eq.getNumberOfPresets();
						int p = s.getIntPref(EQ_PRESET);

						if ((p > 0) && (p <= num)) {
							eq.setEnabled(true);
							eq.usePreset((short) (p - 1));
						} else {
							int[] bands = null;

							if (p < 0) {
								String[] u = getPlaybackControlPrefs().getStringArrayPref(EQ_USER_PRESETS);
								if ((u.length > 0) && ((p = -p - 1) < u.length)) bands = getUserPresetBands(u[p]);
							} else {
								bands = s.getIntArrayPref(EQ_BANDS);
							}

							if (bands != null) {
								eq.setEnabled(true);

								for (short i = 0; (i < bands.length) && (i < num); i++) {
									eq.setBandLevel(i, (short) bands[i]);
								}
							} else {
								eq.setEnabled(false);
							}
						}
					} catch (Exception ex) {
						Log.e(getClass().getName(), "Failed to configure Equalizer", ex);
					}
				} else {
					eq.setEnabled(false);
				}
			}

			if (virt != null) {
				if (s.getBooleanPref(VIRT_ENABLED)) {
					try {
						virt.setEnabled(true);
						virt.setStrength((short) s.getIntPref(VIRT_STRENGTH));
						virt.forceVirtualizationMode(s.getIntPref(VIRT_MODE));
					} catch (Exception ex) {
						Log.e(getClass().getName(), "Failed to configure Virtualizer", ex);
					}
				} else {
					virt.setEnabled(false);
				}
			}

			if (bass != null) {
				if (bass.getStrengthSupported() && s.getBooleanPref(BASS_ENABLED)) {
					try {
						bass.setEnabled(true);
						bass.setStrength((short) s.getIntPref(BASS_STRENGTH));
					} catch (Exception ex) {
						Log.e(getClass().getName(), "Failed to configure BassBoost", ex);
					}
				} else {
					bass.setEnabled(false);
				}
			}

			return;
		}

		if (eq != null) eq.setEnabled(false);
		if (virt != null) virt.setEnabled(false);
		if (bass != null) bass.setEnabled(false);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean requestAudioFocus() {
		return (audioManager == null) ||
				(AudioManagerCompat.requestAudioFocus(audioManager, audioFocusReq) == AUDIOFOCUS_REQUEST_GRANTED);
	}

	private void releaseAudioFocus() {
		if (audioManager != null)
			AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusReq);
	}

	private Runnable timer;

	private void startTimer(PlayableItem i, long pos, float speed) {
		timer = new Runnable() {
			@Override
			public void run() {
				if (timer == this) onSkipToNext();
			}
		};

		long delay = (long) ((i.getDuration() - pos) / speed);
		handler.postDelayed(timer, delay);
	}


	private void stopTimer() {
		timer = null;
	}

	public interface Listener {
		void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state);
	}

	private static final class VideoViewWraper implements Comparable<VideoViewWraper> {
		final VideoView view;
		final int priority;

		VideoViewWraper(VideoView view, int priority) {
			this.view = view;
			this.priority = priority;
		}

		@Override
		public int compareTo(VideoViewWraper o) {
			return Integer.compare(priority, o.priority);
		}

		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		@Override
		public boolean equals(Object obj) {
			return view == ((VideoViewWraper) obj).view;
		}

		@Override
		public int hashCode() {
			return view.hashCode();
		}
	}
}