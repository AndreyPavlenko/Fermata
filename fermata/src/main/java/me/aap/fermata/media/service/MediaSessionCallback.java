package me.aap.fermata.media.service;

import static android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
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
import static me.aap.fermata.action.KeyEventHandler.handleKeyEvent;
import static me.aap.fermata.media.engine.MediaEngine.NO_SUBTITLES;
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
import static me.aap.fermata.media.pref.MediaPrefs.VOL_BOOST_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.VOL_BOOST_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.getUserPresetBands;
import static me.aap.fermata.media.pref.PlaybackControlPrefs.getTimeMillis;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.function.CheckedRunnable.runWithRetry;
import static me.aap.utils.misc.Assert.assertNotNull;
import static me.aap.utils.misc.MiscUtils.ifNotNull;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.Consumer;
import me.aap.utils.holder.Holder;
import me.aap.utils.log.Log;
import me.aap.utils.net.NetServer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;

/**
 * @author Andrey Pavlenko
 */
public class MediaSessionCallback extends MediaSessionCompat.Callback
		implements SharedConstants, MediaSessionCallbackAssistant, MediaEngine.Listener,
		AudioManager.OnAudioFocusChangeListener, EventBroadcaster<MediaSessionCallback.Listener>,
		Closeable {
	public static final String EXTRA_POS = "me.aap.fermata.extra.pos";
	private static final long SUPPORTED_ACTIONS =
			ACTION_PLAY | ACTION_STOP | ACTION_PAUSE | ACTION_PLAY_PAUSE | ACTION_PLAY_FROM_MEDIA_ID |
					ACTION_PLAY_FROM_SEARCH | ACTION_PLAY_FROM_URI | ACTION_SKIP_TO_PREVIOUS |
					ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_QUEUE_ITEM | ACTION_REWIND | ACTION_FAST_FORWARD |
					ACTION_SEEK_TO | ACTION_SET_REPEAT_MODE | ACTION_SET_SHUFFLE_MODE;
	private final Collection<ListenerRef<MediaSessionCallback.Listener>> listeners =
			new LinkedList<>();
	private final MediaLib lib;
	private final FermataMediaService service;
	private final MediaSessionCompat session;
	private final PlaybackControlPrefs playbackControlPrefs;
	private final Handler handler;
	private final AudioManager audioManager;
	private final AudioFocusRequestCompat audioFocusReq;
	private final PlaybackStateCompat.CustomAction customRewind;
	private final PlaybackStateCompat.CustomAction customFastForward;
	private final PlaybackStateCompat.CustomAction customRepeatEnable;
	private final PlaybackStateCompat.CustomAction customRepeatDisable;
	private final PlaybackStateCompat.CustomAction customShuffleEnable;
	private final PlaybackStateCompat.CustomAction customShuffleDisable;
	private final PlaybackStateCompat.CustomAction customFavoritesAdd;
	private final PlaybackStateCompat.CustomAction customFavoritesRemove;
	private final BroadcastReceiver onNoisy;
	private MediaEngine engine;
	private boolean playOnPrepared;
	private boolean playOnAudioFocus;
	private boolean tryAnotherEngine;
	@NonNull
	private PlaybackStateCompat currentState;
	private MediaMetadataCompat currentMetadata;
	private Queue<Prioritized<VideoView>> videoView;
	private Queue<Prioritized<MediaSessionCallbackAssistant>> assistants;
	private FutureSupplier<?> playerTask = completedVoid();

	public MediaSessionCallback(FermataMediaService service, MediaSessionCompat session,
															MediaLib lib,
															PlaybackControlPrefs playbackControlPrefs, Handler handler) {
		this.lib = lib;
		this.service = service;
		this.session = session;
		this.playbackControlPrefs = playbackControlPrefs;
		this.handler = handler;
		Context ctx = lib.getContext();

		customRewind = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_RW,
				ctx.getString(R.string.rewind), R.drawable.rw).build();
		customFastForward = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FF,
				ctx.getString(R.string.fast_forward), R.drawable.ff).build();
		customRepeatEnable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_REPEAT_ENABLE,
				ctx.getString(R.string.repeat), R.drawable.repeat).build();
		customRepeatDisable =
				new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_REPEAT_DISABLE,
						ctx.getString(R.string.repeat_disable), R.drawable.repeat_filled).build();
		customShuffleEnable =
				new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_SHUFFLE_ENABLE,
						ctx.getString(R.string.shuffle), R.drawable.shuffle).build();
		customShuffleDisable =
				new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_SHUFFLE_DISABLE,
						ctx.getString(R.string.shuffle_disable), R.drawable.shuffle_filled).build();
		customFavoritesAdd = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FAVORITES_ADD,
				ctx.getString(R.string.favorites_add), R.drawable.favorite).build();
		customFavoritesRemove =
				new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FAVORITES_REMOVE,
						ctx.getString(R.string.favorites_remove), R.drawable.favorite_filled).build();

		currentState = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS).build();
		setPlaybackState(currentState);
		session.setActive(true);

		audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

		if (audioManager != null) {
			AudioAttributesCompat focusAttrs =
					new AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
							.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build();
			audioFocusReq = new AudioFocusRequestCompat.Builder(
					AudioManagerCompat.AUDIOFOCUS_GAIN).setAudioAttributes(focusAttrs)
					.setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(this).build();
		} else {
			audioFocusReq = null;
		}

		onNoisy = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
					Log.i("Received ACTION_AUDIO_BECOMING_NOISY event");
					onPause();
				}
			}
		};
		ctx.registerReceiver(onNoisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
	}

	public Context getContext() {
		return getMediaLib().getContext();
	}

	public MediaLib getMediaLib() {
		return lib;
	}

	public MediaEngineManager getEngineManager() {
		return lib.getMediaEngineManager();
	}

	@Nullable
	public MediaEngine getEngine() {
		return engine;
	}

	public void setEngine(MediaEngine engine) {
		if (this.engine == engine) return;
		playerTask.cancel();
		onStop();
		this.engine = engine;
	}

	@Nullable
	public PlayableItem getCurrentItem() {
		MediaEngine eng = getEngine();
		return (eng == null) ? null : eng.getSource();
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

	public void addVideoView(VideoView view, int priority) {
		if (this.videoView == null) {
			videoView = new PriorityQueue<>(2);
		} else {
			for (Prioritized<VideoView> s : videoView) {
				if (s.obj == view) return;
			}
		}

		videoView.add(new Prioritized<>(view, priority));
		MediaEngine eng = getEngine();

		if (eng != null) {
			PlayableItem i = eng.getSource();
			if (i.isVideo()) eng.setVideoView(getVideoView());
		}
	}

	public void removeVideoView(VideoView view) {
		MediaEngine eng = getEngine();

		if (removeFromQueue(videoView, view)) {
			if (videoView.isEmpty()) {
				videoView = null;
				if (eng != null) eng.setVideoView(null);
			} else if (eng != null) {
				eng.setVideoView(getVideoView());
			}
		}
	}

	@Nullable
	public VideoView getVideoView() {
		if (videoView == null) return null;
		Prioritized<VideoView> w = videoView.peek();
		return (w == null) ? null : w.obj;
	}

	public void addAssistant(MediaSessionCallbackAssistant a, int priority) {
		if (assistants == null) assistants = new PriorityQueue<>(2);
		assistants.add(new Prioritized<>(a, priority));
	}

	public void removeAssistant(MediaSessionCallbackAssistant a) {
		removeFromQueue(assistants, a);
	}

	@NonNull
	public Handler getHandler() {
		return handler;
	}

	@NonNull
	public MediaSessionCallbackAssistant getAssistant() {
		if (assistants == null) return this;
		Prioritized<MediaSessionCallbackAssistant> w = assistants.peek();
		return (w == null) ? this : w.obj;
	}

	public boolean hasCustomEngineProvider() {
		return getEngineManager().hasCustomEngineProvider();
	}

	public void setCustomEngineProvider(@NonNull MediaEngineProvider engineProvider) {
		getEngineManager().setCustomEngineProvider(engineProvider);
		if (getEngine() != null) {
			if (isPlaying()) onStop(true).onSuccess(v -> handler.post(this::play));
			else onStop();
		}
	}

	public void removeCustomEngineProvider(MediaEngineProvider engineProvider) {
		if (getEngineManager().removeCustomEngineProvider(engineProvider)) {
			if (isPlaying()) onStop(true).onSuccess(v -> handler.post(this::play));
			else onStop();
		}
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable(Item i) {
		MediaSessionCallbackAssistant a = getAssistant();
		return (a == this) ? MediaSessionCallbackAssistant.super.getPrevPlayable(i) :
				a.getPrevPlayable(i);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable(Item i) {
		MediaSessionCallbackAssistant a = getAssistant();
		return (a == this) ? MediaSessionCallbackAssistant.super.getNextPlayable(i) :
				a.getNextPlayable(i);
	}

	private static <T> boolean removeFromQueue(Queue<Prioritized<T>> q, T t) {
		if (q == null) return false;
		for (Iterator<Prioritized<T>> it = q.iterator(); it.hasNext(); ) {
			if (it.next().obj == t) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
		KeyEvent e = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
		if (e == null) return super.onMediaButtonEvent(mediaButtonEvent);
		return handleKeyEvent(this, e, (i, ke) -> super.onMediaButtonEvent(mediaButtonEvent));
	}

	public void close() {
		onStop();
		session.setActive(false);
		lib.getContext().unregisterReceiver(onNoisy);
		removeBroadcastListeners();
	}

	@Override
	public void onPrepare() {
		playerTask.cancel();
		playerTask = prepare();
	}

	private FutureSupplier<Void> prepare() {
		int st = getPlaybackState().getState();

		if ((st != PlaybackState.STATE_NONE) && (st != PlaybackState.STATE_ERROR)) {
			return completedVoid();
		}

		return lib.getLastPlayedItem().then(this::prepareItem).then(i -> {
			if ((i == null) || i.isVideo() || !i.isSeekable()) return completedVoid();

			engine = getEngineManager().createEngine(engine, i, this);
			Log.d("MediaEngine ", engine + " created for ", i);
			if (engine == null) return completedVoid();

			playOnPrepared = false;
			if (i.isVideo() && (videoView != null)) engine.setVideoView(getVideoView());
			tryAnotherEngine = true;
			engine.prepare(i);
			return completedVoid();
		});
	}

	@Override
	public void onPlay() {
		playerTask.cancel();
		playerTask = play();
	}

	@SuppressLint("SwitchIntDef")
	public FutureSupplier<Void> play() {
		PlaybackStateCompat state = getPlaybackState();

		switch (state.getState()) {
			case PlaybackStateCompat.STATE_NONE:
			case PlaybackStateCompat.STATE_STOPPED:
			case PlaybackStateCompat.STATE_ERROR:
				return lib.getLastPlayedItem().then(this::prepareItem).then(i -> {
					if (i != null) playPreparedItem(i, lib.getLastPlayedPosition(i));
					return completedVoid();
				});
			case PlaybackStateCompat.STATE_PAUSED:
				MediaEngine eng = getEngine();
				assert (eng != null);
				assert (eng.getSource() != null);

				if (!eng.requestAudioFocus(audioManager, audioFocusReq)) {
					Log.i("Audio focus request failed");
					return completedVoid();
				}


				long pos = state.getPosition();
				float speed = getSpeed(engine.getSource());
				state =
						new PlaybackStateCompat.Builder(state).setState(PlaybackStateCompat.STATE_PLAYING, pos,
								speed).build();
				setPlaybackState(state);
				eng.setPosition(pos);
				start(eng, speed);
				break;
			default:
				break;
		}

		return completedVoid();
	}

	@Override
	public void onPlayFromMediaId(String mediaId, Bundle extras) {
		playerTask.cancel();
		playerTask = playFromMediaId(mediaId, extras);
	}

	private FutureSupplier<Void> playFromMediaId(String mediaId, Bundle extras) {
		return lib.getItem(mediaId).then(i -> {
			if (i instanceof PlayableItem) {
				return completed((PlayableItem) i);
			} else if (i instanceof BrowsableItem) {
				return ((BrowsableItem) i).getFirstPlayable();
			} else {
				return completedNull();
			}
		}).then(this::prepareItem).then(pi -> {
			if (pi != null) {
				long pos = (extras == null) ? 0 : extras.getLong(EXTRA_POS, 0);
				playPreparedItem(pi, pos);
			} else {
				String msg =
						lib.getContext().getResources().getString(R.string.err_failed_to_play, mediaId);
				Log.w(msg);
				PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
						.setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
						.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
				setPlaybackState(state);
			}

			return completedVoid();
		});
	}

	@Override
	public void onPlayFromSearch(String query, Bundle extras) {
		Log.i("Search query received: " + query);
		getMediaLib().getMetadataRetriever().queryId(query).onSuccess(id -> {
			if (id != null) {
				Log.i("Playing media from search: " + id);
				onPlayFromMediaId(id, null);
			} else {
				Log.i("No media found for query: " + query + ". Playing last item");
			}
		});
	}

	@Override
	public void onPause() {
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return;

		if (!eng.canPause()) {
			onStop();
			return;
		}

		eng.pause();
		eng.getPosition().and(eng.getSpeed()).main().onSuccess(h -> {
			if (eng != getEngine()) return;
			long qid = currentState.getActiveQueueItemId();
			setLastPlayed(i, h.value1);
			PlaybackStateCompat state = createPlayingState(i, true, qid, h.value1, h.value2);
			setPlaybackState(state);
		});
	}

	@Override
	public void onStop() {
		onStop(true);
	}

	private FutureSupplier<?> onStop(boolean setPosition) {
		MediaEngine eng = getEngine();

		if (setPosition && (eng != null)) {
			PlayableItem i = eng.getSource();
			if ((i != null) && i.isExternal()) return onStop(eng, -1);
			else return eng.getPosition().main().then(pos -> onStop(eng, pos));
		} else {
			return onStop(eng, -1);
		}
	}

	private FutureSupplier<?> onStop(MediaEngine eng, long pos) {
		if (eng != null) {
			if (pos != -1) {
				PlayableItem i = eng.getSource();
				if (i != null) setLastPlayed(i, pos);
			}

			eng.stop();
			eng.releaseAudioFocus(audioManager, audioFocusReq);
			eng.close();
			if (eng == engine) engine = null;
		}

		stopped();
		return completedVoid();
	}

	private void stopped() {
		if (getPlaybackState().getState() != PlaybackStateCompat.STATE_STOPPED) {
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f).build();
			setPlaybackState(state, null, Collections.emptyList(), REPEAT_MODE_INVALID,
					SHUFFLE_MODE_INVALID);
		}

		session.setQueue(null);
		session.setActive(false);
	}

	@Override
	public void onSeekTo(long position) {
		MediaEngine eng = getEngine();
		if ((eng == null) || (eng.getSource() == null)) return;

		eng.getSpeed().onSuccess(speed -> {
			PlaybackStateCompat state = getPlaybackState();
			eng.setPosition(position);
			PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
			b.setState(state.getState(), position, speed);
			setPlaybackState(b.build());
		});
	}

	@Override
	public void onSkipToPrevious() {
		playerTask.cancel();
		playerTask = skipTo(false);
	}

	@Override
	public void onSkipToNext() {
		playerTask.cancel();
		playerTask = skipTo(true);
	}

	private FutureSupplier<Void> skipTo(boolean next) {
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return completedVoid();

		return (next ? getNextPlayable(i) : getPrevPlayable(i)).then(this::prepareItem).then(pi -> {
			if (pi != null) skipTo(next, pi);
			return completedVoid();
		});
	}

	private void skipTo(boolean next, PlayableItem i) {
		PlaybackStateCompat state = getPlaybackState();
		long pos = i.getPrefs().getPositionPref();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(next ? STATE_SKIPPING_TO_NEXT : STATE_SKIPPING_TO_PREVIOUS, pos,
				state.getPlaybackSpeed());
		setPlaybackState(b.build());
		playPreparedItem(i, pos);
	}

	@Override
	public void onRewind() {
		rewindFastForward(false, 1);
	}

	@Override
	public void onFastForward() {
		rewindFastForward(true, 1);
	}

	public void rewindFastForward(boolean ff, int multiply) {
		PlaybackControlPrefs pp = getPlaybackControlPrefs();
		rewindFastForward(ff, pp.getRwFfTimePref(), pp.getRwFfTimeUnitPref(), multiply);
	}

	public boolean rewindFastForward(boolean ff, int time, int timeUnit, int multiply) {
		playerTask.cancel();
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return false;

		playerTask = eng.getDuration().and(eng.getPosition()).main().onSuccess(
				h -> rewindFastForward(eng, i, h.value2, h.value1, ff, time, timeUnit, multiply));
		return true;
	}

	private void rewindFastForward(MediaEngine eng, PlayableItem i, long pos, long dur, boolean ff,
																 int time, int timeUnit, int multiply) {
		if (getCurrentItem() != i) return;

		PlaybackStateCompat state = getPlaybackState();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(ff ? STATE_FAST_FORWARDING : STATE_REWINDING, state.getPosition(),
				state.getPlaybackSpeed());
		setPlaybackState(b.build());
		long timeShift = getTimeMillis(dur, time, timeUnit) * Math.max(1, multiply);

		if (ff) {
			dur -= 1000;
			if (dur <= 0) return;
			pos = Math.min(pos + timeShift, dur);
		} else {
			pos -= timeShift;
			if (pos < 0) pos = 0;
		}

		eng.setPosition(pos);
		setPlaybackState(b.setState(state.getState(), pos, state.getPlaybackSpeed()).build());
	}

	@Override
	public void onCustomAction(String action, Bundle extras) {
		switch (action) {
			case CUSTOM_ACTION_RW -> onRewind();
			case CUSTOM_ACTION_FF -> onFastForward();
			case CUSTOM_ACTION_REPEAT_ENABLE -> repeatEnableDisable(true);
			case CUSTOM_ACTION_REPEAT_DISABLE -> repeatEnableDisable(false);
			case CUSTOM_ACTION_SHUFFLE_ENABLE -> shuffleEnableDisable(true);
			case CUSTOM_ACTION_SHUFFLE_DISABLE -> shuffleEnableDisable(false);
			case CUSTOM_ACTION_FAVORITES_ADD -> favoriteAddRemove(true);
			case CUSTOM_ACTION_FAVORITES_REMOVE -> favoriteAddRemove(false);
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

	private void shuffleEnableDisable(boolean enable) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		PlaybackStateCompat state = getPlaybackState();
		List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();
		i.getParent().getPrefs().setShufflePref(enable);

		if (enable) {
			CollectionUtils.replace(actions, customShuffleEnable, customShuffleDisable);
		} else {
			CollectionUtils.replace(actions, customShuffleDisable, customShuffleEnable);
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

		if (i.getParent() == favorites) {
			favorites.getQueue().main().onSuccess(q -> {
				if (i != getCurrentItem()) return;
				session.setQueue(q);
				String id = i.getId();

				for (QueueItem qi : q) {
					if (id.equals(qi.getDescription().getMediaId())) {
						PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
						b.setActiveQueueItemId(qi.getQueueId()).build();
						setPlaybackState(b.build(), q);
					}
				}
			});
		} else {
			setPlaybackState(new PlaybackStateCompat.Builder(state).build());
		}
	}

	@Override
	public void onSkipToQueueItem(long queueId) {
		PlayableItem pi = getCurrentItem();
		if (pi == null) return;

		playerTask.cancel();
		playerTask = skipToQueueItem(pi, queueId);
	}

	private FutureSupplier<Void> skipToQueueItem(PlayableItem pi, long queueId) {
		return pi.getParent().getChildren().then(children -> {
			if ((queueId < 0) || (queueId >= children.size())) return completedNull();

			Item i = children.get((int) queueId);
			if (i instanceof PlayableItem) return completed((PlayableItem) i);
			else if (i instanceof BrowsableItem) return ((BrowsableItem) i).getFirstPlayable();
			else return completedNull();
		}).then(this::prepareItem).then(i -> {
			if (i == null) return completedVoid();

			PlaybackStateCompat state = getPlaybackState();
			PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
			b.setState(PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM, state.getPosition(),
					state.getPlaybackSpeed());
			setPlaybackState(b.build());
			playPreparedItem(i, 0);
			return completedVoid();
		});
	}

	@Override
	public void onSetShuffleMode(int shuffleMode) {
		shuffleEnableDisable(shuffleMode != SHUFFLE_MODE_NONE);
	}

	@Override
	public void onSetRepeatMode(int repeatMode) {
		PlayableItem i = getCurrentItem();
		if (i == null) return;

		BrowsableItemPrefs p = i.getParent().getPrefs();

		switch (repeatMode) {
			case PlaybackStateCompat.REPEAT_MODE_INVALID, REPEAT_MODE_NONE -> {
				p.setRepeatItemPref(null);
				p.setRepeatPref(false);
			}
			case REPEAT_MODE_ONE -> {
				p.setRepeatItemPref(i.getId());
				p.setRepeatPref(false);
			}
			case REPEAT_MODE_ALL, REPEAT_MODE_GROUP -> {
				p.setRepeatItemPref(null);
				p.setRepeatPref(true);
			}
		}
	}

	@Override
	public void onSetPlaybackSpeed(float speed) {
		MediaEngine eng = getEngine();
		if (eng != null) eng.setSpeed(speed);
	}

	@Override
	public void onEnginePrepared(MediaEngine engine) {
		playerTask.cancel();
		PlayableItem i = engine.getSource();
		if (i != null) onEnginePrepared(engine, i);
	}

	private void onEnginePrepared(MediaEngine engine, PlayableItem i) {
		long pos = lib.getLastPlayedPosition(i);

		if (pos > 0) {
			FutureSupplier<Long> dur = i.getDuration();

			if (dur.isDone()) {
				if (pos <= dur.get(() -> 0L)) engine.setPosition(pos);
			} else {
				dur.main().onSuccess(d -> {
					if ((this.engine != engine) || (engine.getSource() != i)) return;
					engine.setPosition((pos > d) ? 0 : pos);
				});
			}
		}

		float speed = getSpeed(i);
		PlayableItemPrefs prefs = i.getPrefs();
		BrowsableItemPrefs parentPrefs = i.getParent().getPrefs();
		PlaybackControlPrefs playbackPrefs = getPlaybackControlPrefs();
		runWithRetry(() -> setAudiEffects(engine, prefs, parentPrefs, playbackPrefs));

		if (playOnPrepared) {
			setLastPlayed(i, pos);
			start(engine, speed);
		} else {
			setPlayingState(engine, false, pos, speed);
		}
	}

	@Override
	public void onEngineStarted(MediaEngine engine) {
		engine.getPosition().and(engine.getSpeed()).main()
				.onSuccess(h -> setPlayingState(engine, true, h.value1, h.value2));
	}

	private void setPlayingState(MediaEngine engine, boolean playing, long pos, float speed) {
		PlayableItem i = engine.getSource();
		BrowsableItemPrefs prefs = i.getParent().getPrefs();
		int shuffle = prefs.getShufflePref() ? SHUFFLE_MODE_ALL : SHUFFLE_MODE_NONE;
		int repeat;

		if (prefs.getRepeatPref()) {
			repeat = REPEAT_MODE_ALL;
		} else if (i.getId().equals(prefs.getRepeatItemPref())) {
			repeat = REPEAT_MODE_ONE;
		} else {
			repeat = REPEAT_MODE_NONE;
		}

		session.setRepeatMode(repeat);
		session.setShuffleMode(shuffle);

		FutureSupplier<Long> getQid = i.getQueueId();
		Holder<MediaMetadataCompat> mdHolder = new Holder<>();
		Holder<Consumer<MediaMetadataCompat>> update = new Holder<>(mdHolder::set);

		FutureSupplier<Void> load = i.getMediaData().main().then(md1 -> {
			update.get().accept(md1);

			return getQid.then(qid -> i.getMediaDescription().main().then(dsc -> {
				if (getCurrentItem() != i) return completedVoid();
				MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder(md1);
				FutureSupplier<MediaMetadataCompat> md2 = buildMetadata(b, md1, dsc);

				if (md2.isDone()) {
					update.get().accept(md2.get(b::build));
					return completedVoid();
				} else {
					update.get().accept(b.build());
					return md2.main().then(md3 -> {
						update.get().accept(md3);
						return completedVoid();
					});
				}
			}));
		});

		MediaMetadataCompat md;

		if (load.isDone() && !load.isFailed()) {
			md = mdHolder.get();
			assertNotNull(md);
		} else {
			MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
			b.putString(METADATA_KEY_DISPLAY_TITLE, i.getResource().getName());
			md = b.build();
			update.set(m -> engine.getPosition().main().onSuccess(position -> {
				if (getCurrentItem() != i) return;
				PlaybackStateCompat s =
						createPlayingState(i, !isPlaying(), getQid.peek(0L), position, speed);
				session.setMetadata(m);
				setPlaybackState(s, m, null, repeat, shuffle);
			}));
		}

		PlaybackStateCompat s = createPlayingState(i, !playing, getQid.peek(0L), pos, speed);
		session.setMetadata(md);
		setPlaybackState(s, md, null, repeat, shuffle);
	}

	private FutureSupplier<MediaMetadataCompat> buildMetadata(MediaMetadataCompat.Builder b,
																														MediaMetadataCompat meta,
																														MediaDescriptionCompat dsc) {
		ifNotNull(dsc.getTitle(), t -> b.putString(METADATA_KEY_DISPLAY_TITLE, t.toString()));
		ifNotNull(dsc.getSubtitle(), t -> b.putString(METADATA_KEY_DISPLAY_SUBTITLE, t.toString()));
		if (meta.getBitmap(METADATA_KEY_ALBUM_ART) != null) return completed(b.build());

		String art = meta.getString(METADATA_KEY_ALBUM_ART_URI);

		if (art != null) {
			b.putString(METADATA_KEY_ALBUM_ART_URI, null);
			return lib.getBitmap(art).then(bm -> {
				b.putBitmap(METADATA_KEY_ALBUM_ART, (bm != null) ? bm : getDefaultImage());
				return completed(b.build());
			});
		}

		Uri uri = dsc.getIconUri();

		if (uri != null) {
			return lib.getBitmap(uri.toString()).then(bm -> {
				b.putBitmap(METADATA_KEY_ALBUM_ART, (bm != null) ? bm : getDefaultImage());
				return completed(b.build());
			});
		}

		b.putBitmap(METADATA_KEY_ALBUM_ART, getDefaultImage());
		return completed(b.build());
	}

	@Override
	public void onEngineEnded(MediaEngine engine) {
		playerTask.cancel();
		playerTask = engineEnded(engine);
	}

	private FutureSupplier<?> engineEnded(MediaEngine engine) {
		PlayableItem i = engine.getSource();

		if (i != null) {
			if (i instanceof StreamItem) {
				Log.w("Failed to play stream? Retrying ", i);
				playItem(i, 0);
				return playerTask;
			}

			if (i.isVideo()) i.getPrefs().setWatchedPref(true);

			if (!i.getParent().getPrefs().getPlayNextPref()) {
				onStop(true);
				return completedVoid();
			}

			return getNextPlayable(i).then(this::prepareItem).then(next -> {
				if (next != null) {
					skipTo(true, next);
				} else {
					onStop(false);
					setLastPlayed(i, 0);
				}

				return completedVoid();
			});
		} else {
			onStop(false);
			return completedVoid();
		}
	}

	@Override
	public void onVideoSizeChanged(MediaEngine engine, int width, int height) {
		VideoView v = getVideoView();
		if (v != null) v.setSurfaceSize(engine);
	}

	@Override
	public void onSubtitleStreamChanged(MediaEngine engine, @Nullable SubtitleStreamInfo info) {
		fireBroadcastEvent(l -> l.onSubtitleStreamChanged(this, info));
	}

	@Override
	public void onEngineError(MediaEngine engine, Throwable ex) {
		String msg;
		PlayableItem i = engine.getSource();

		if (TextUtils.isEmpty(ex.getLocalizedMessage())) {
			msg = lib.getContext().getResources().getString(R.string.err_failed_to_play, i);
		} else {
			msg = lib.getContext().getResources()
					.getString(R.string.err_failed_to_play_cause, i, ex.getLocalizedMessage());
		}

		Log.w(ex, msg);

		if (tryAnotherEngine && (engine.getSource() != null)) {
			this.engine = getEngineManager().createAnotherEngine(engine, this);

			if (this.engine != null) {
				Log.i("Trying another engine: ", this.engine);
				tryAnotherEngine = false;
				if (i.isVideo() && (videoView != null)) this.engine.setVideoView(getVideoView());
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
		Log.i("Audio focus event received: ", focusChange);

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

	public void playItem(PlayableItem i, long pos) {
		playerTask.cancel();
		playerTask = prepareItem(i).onSuccess(pi -> playPreparedItem(i, pos));
	}

	private void playPreparedItem(PlayableItem i, long pos) {
		MediaEngine eng = getEngine();

		if (eng != null) {
			PlayableItem current = eng.getSource();

			if ((current != null) && !current.isExternal()) {
				if (current instanceof StreamItem) {
					playPreparedItem(eng, i, pos, current, 0);
				} else {
					eng.getPosition().main()
							.onSuccess(currentPos -> playPreparedItem(eng, i, pos, current, currentPos));
				}
				return;
			}
		}

		playPreparedItem(eng, i, pos, null, -1);
	}

	private void playPreparedItem(MediaEngine eng, PlayableItem i, long pos, PlayableItem current,
																long currentPos) {
		engine = eng = getEngineManager().createEngine(eng, i, this);

		if (eng == null) {
			if (current != null) setLastPlayed(current, currentPos);
			String msg =
					lib.getContext().getResources().getString(R.string.err_unsupported_source_type, i);
			PlaybackStateCompat state = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
					.setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
					.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, msg).build();
			setPlaybackState(state, Collections.emptyList());
			return;
		}

		BrowsableItem p = i.getParent();
		boolean updateQueue = false;

		if (current != null) {
			setLastPlayed(current, currentPos);
			if (current.equals(i)) {
				if (pos != -1) eng.setPosition(pos);
			} else {
				setLastPlayed(i, pos);
				if (!p.equals(current.getParent())) updateQueue = true;
			}
		} else {
			updateQueue = true;
			setLastPlayed(i, pos);
		}

		if (i.isVideo() && (videoView != null)) engine.setVideoView(getVideoView());

		playOnPrepared = true;
		tryAnotherEngine = true;

		if (!eng.requestAudioFocus(audioManager, audioFocusReq)) {
			Log.i("Audio focus request failed");
			return;
		}

		eng.prepare(i);

		if (updateQueue) {
			p.getQueue().main().onSuccess(q -> {
				if ((engine == null) || (engine.getSource() != i)) return;
				session.setQueue(q);
				service.updateSessionState(null, null, q, REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID);
			});
		}
	}

	private PlaybackStateCompat createPlayingState(PlayableItem i, boolean pause, long qid,
																								 long position, float speed) {
		int state = pause ? PlaybackStateCompat.STATE_PAUSED : PlaybackStateCompat.STATE_PLAYING;
		BrowsableItemPrefs p = i.getParent().getPrefs();
		boolean repeat = p.getRepeatPref();
		boolean shuffle = p.getShufflePref();
		return new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS)
				.setState(state, position, speed).setActiveQueueItemId(qid).addCustomAction(customRewind)
				.addCustomAction(customFastForward)
				.addCustomAction(repeat ? customRepeatDisable : customRepeatEnable)
				.addCustomAction(shuffle ? customShuffleDisable : customShuffleEnable)
				.addCustomAction(i.isFavoriteItem() ? customFavoritesRemove : customFavoritesAdd).build();
	}

	private void setPlaybackState(PlaybackStateCompat state) {
		setPlaybackState(state, null);
	}

	private void setPlaybackState(PlaybackStateCompat state, List<QueueItem> queue) {
		setPlaybackState(state, null, queue, REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID);
	}

	private void setPlaybackState(PlaybackStateCompat state, MediaMetadataCompat meta,
																List<QueueItem> queue, int repeat, int shuffle) {
		currentState = state;
		currentMetadata = meta;
		session.setPlaybackState(state);
		service.updateSessionState(state, meta, queue, repeat, shuffle);
		service.updateNotification(state.getState(), getCurrentItem());
		fireBroadcastEvent(l -> l.onPlaybackStateChanged(this, state));

		if (state.getState() == STATE_PLAYING) {
			MediaEngine engine = getEngine();
			if (engine == null) {
				stopTimer();
				return;
			}
			PlayableItem i = engine.getSource();
			if (i.isTimerRequired()) startTimer(i, state.getPosition(), state.getPlaybackSpeed());
		} else {
			stopTimer();
		}
	}

	@NonNull
	public PlaybackStateCompat getPlaybackState() {
		return currentState;
	}

	public MediaMetadataCompat getMetadata() {
		return currentMetadata;
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
		LoudnessEnhancer le = ae.getLoudnessEnhancer();

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
						Log.e(ex, "Failed to configure Equalizer");
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
						Log.e(ex, "Failed to configure Virtualizer");
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
						Log.e(ex, "Failed to configure BassBoost");
					}
				} else {
					bass.setEnabled(false);
				}
			}

			if (le != null) {
				if (s.getBooleanPref(VOL_BOOST_ENABLED)) {
					try {
						le.setEnabled(true);
						le.setTargetGain(s.getIntPref(VOL_BOOST_STRENGTH) * 10);
					} catch (Exception ex) {
						Log.e(ex, "Failed to configure LoudnessEnhancer");
					}
				} else {
					le.setEnabled(false);
				}
			}

			return;
		}

		if (eq != null) eq.setEnabled(false);
		if (virt != null) virt.setEnabled(false);
		if (bass != null) bass.setEnabled(false);
		if (le != null) le.setEnabled(false);
	}

	private FutureSupplier<PlayableItem> prepareItem(PlayableItem i) {
		if (i == null) return completedNull();

		// Make sure metadata is loaded
		FutureSupplier<Long> getDur = i.getDuration();

		// Make sure HTTP server is started
		if (i.isNetResource()) {
			FutureSupplier<NetServer> start = lib.getVfsManager().getNetServer();
			if (!start.isDone()) return start.and(getDur, (s, d) -> {
			}).map(v -> i).main();
		}

		if (!getDur.isDone()) return getDur.map(d -> i).timeout(5000, () -> i).main();
		return completed(i).main();
	}

	private void setLastPlayed(PlayableItem i, long position) {
		if ((position < 0) || i.isExternal()) return;

		i.getDuration().main().onSuccess(dur -> {
			String id;
			MediaLibPrefs libPrefs = lib.getPrefs();
			BrowsableItemPrefs p;

			if (i.isStream() || (dur <= 0)) {
				id = i.getId();
				p = i.getParent().getPrefs();
				libPrefs.setLastPlayedItemPref(id);
				libPrefs.setLastPlayedPosPref(0);
				p.setLastPlayedItemPref(id);
				p.setLastPlayedPosPref(0);
				return;
			}

			if ((dur - position) <= 1000) {
				i.getNextPlayable().onCompletion((next, fail) -> {
					if (next == null) next = i;

					String nextId = next.getId();
					BrowsableItemPrefs nextPrefs = next.getParent().getPrefs();
					libPrefs.setLastPlayedItemPref(nextId);
					libPrefs.setLastPlayedPosPref(0);
					nextPrefs.setLastPlayedItemPref(nextId);
					nextPrefs.setLastPlayedPosPref(0);
					i.getPrefs().setPositionPref(0);
				});

				return;
			} else {
				id = i.getId();
				p = i.getParent().getPrefs();
			}

			if (i.isVideo()) {
				PlayableItemPrefs prefs = i.getPrefs();
				float th = prefs.getWatchedThresholdPref() / 100F;
				if (th > 0) {
					if (position > (dur * th)) prefs.setWatchedPref(true);
					else prefs.setPositionPref(position);
				}
			} else if (position == 0) {
				i.getPrefs().setPositionPref(0);
			} else {
				MediaEngine eng = getEngine();

				if ((eng != null) && (eng.getSource() == i) &&
						(eng.getCurrentSubtitles() != NO_SUBTITLES)) {
					i.getPrefs().setPositionPref(position);
				}
			}

			libPrefs.setLastPlayedItemPref(id);
			libPrefs.setLastPlayedPosPref(position);
			p.setLastPlayedItemPref(id);
			p.setLastPlayedPosPref(position);
		});
	}

	private Runnable timer;

	private void startTimer(PlayableItem i, long pos, float speed) {
		i.getDuration().main().onSuccess(dur -> {
			timer = new Runnable() {
				@Override
				public void run() {
					if (timer == this) onSkipToNext();
				}
			};

			long delay = (long) ((dur - pos) / speed);
			handler.postDelayed(timer, delay);
		});
	}

	private void stopTimer() {
		timer = null;
	}

	private Bitmap defaultImage;

	private Bitmap getDefaultImage() {
		if (defaultImage == null) {
			try {
				FermataApplication app = FermataApplication.get();
				Drawable d = app.getPackageManager().getApplicationIcon(app.getPackageName());
				defaultImage = UiUtils.getBitmap(d);
			} catch (Exception ex) {
				Log.e(ex, "Failed to get application icon");
			}
		}
		return defaultImage;
	}

	boolean isDefaultImage(Bitmap icon) {
		return (icon != null) && (defaultImage != null) && (icon.sameAs(defaultImage));
	}

	public interface Listener {
		default void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {}

		default void onSubtitleStreamChanged(MediaSessionCallback cb,
																				 @Nullable SubtitleStreamInfo info) {}
	}

	private static final class Prioritized<T> implements Comparable<Prioritized<T>> {
		final T obj;
		final int priority;

		Prioritized(T obj, int priority) {
			this.obj = obj;
			this.priority = priority;
		}

		@Override
		public int compareTo(Prioritized<T> o) {
			return Integer.compare(priority, o.priority);
		}

		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		@Override
		public boolean equals(Object obj) {
			return this.obj == ((Prioritized<?>) obj).obj;
		}

		@Override
		public int hashCode() {
			return obj.hashCode();
		}
	}

	private PlaybackTimer playbackTimer;

	public int getPlaybackTimer() {
		return (playbackTimer == null) ? 0 :
				Math.max((int) (playbackTimer.time - System.currentTimeMillis()) / 1000, 0);
	}

	public void setPlaybackTimer(int time) {
		if (time == 0) {
			playbackTimer = null;
		} else {
			int delay = time * 1000;
			PlaybackTimer timer =
					this.playbackTimer = new PlaybackTimer(delay + System.currentTimeMillis());
			handler.postDelayed(timer, delay);
		}
	}

	private final class PlaybackTimer implements Runnable {
		final long time;

		public PlaybackTimer(long time) {
			this.time = time;
		}

		@Override
		public void run() {
			if (playbackTimer == this) onStop();
		}
	}
}