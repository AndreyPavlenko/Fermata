package me.aap.fermata.media.engine;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO;
import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class MediaPlayerEngine extends MediaEngineBase
		implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
		MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnErrorListener {
	private final Context ctx;
	private final MediaPlayer player;
	private final AudioEffects audioEffects;
	private PlayableItem source;

	public MediaPlayerEngine(Context ctx, Listener listener) {
		super(listener);
		this.ctx = ctx;
		player = new MediaPlayer();
		int sessionId = player.getAudioSessionId();
		audioEffects = AudioEffects.create(0, sessionId);
		AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
		player.setAudioAttributes(attrs);
		player.setOnPreparedListener(this);
		player.setOnCompletionListener(this);
		player.setOnErrorListener(this);
		player.setOnVideoSizeChangedListener(this);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_MP;
	}

	@Override
	public void prepare(PlayableItem source) {
		stopped(false);
		this.source = source;
		Uri u = source.getLocation();

		try {
			player.reset();
			String scheme = u.getScheme();
			if (SCHEME_CONTENT.equals(scheme)) {
				player.setDataSource(ctx, u);
			} else if ((scheme != null) && scheme.startsWith("http")) {
				String agent = source.getUserAgent();
				if (agent != null) {
					player.setDataSource(ctx, u, Collections.singletonMap("User-Agent", agent));
				} else {
					player.setDataSource(u.toString());
				}
			} else {
				player.setDataSource(ctx, u);
			}
			player.prepareAsync();
		} catch (Exception ex) {
			listener.onEngineError(this, ex);
			this.source = null;
		}
	}

	@Override
	public void start() {
		player.start();
		started();
		listener.onEngineStarted(this);
	}

	@Override
	public void stop() {
		stopped(false);
		player.stop();
		player.reset();
		source = null;
	}

	@Override
	public void pause() {
		stopped(true);
		player.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return completed((source == null) || !source.isSeekable() ? 0L : player.getDuration());
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		long pos = pos();
		syncSub(pos, speed(), false);
		return completed(pos);
	}

	private long pos() {
		return (source != null) ? (player.getCurrentPosition() - source.getOffset()) : 0;
	}

	@Override
	public void setPosition(long position) {
		if (source == null) return;
		long pos = source.getOffset() + position;
		player.seekTo((int) pos);
		syncSub(pos, speed(), true);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(speed());
	}

	private float speed() {
		try {
			return player.getPlaybackParams().getSpeed();
		} catch (IllegalStateException ex) {
			Log.d(ex);
			return 1f;
		}
	}

	@Override
	public void setSpeed(float speed) {
		try {
			PlaybackParams p = player.getPlaybackParams();
			p.setSpeed(speed);
			player.setPlaybackParams(p);
			syncSub(pos(), speed, true);
		} catch (Exception ex) {
			Log.e(ex, "Failed to set speed: ", speed);
		}
	}

	@Override
	public void setVideoView(VideoView view) {
		try {
			super.setVideoView(view);
			player.setDisplay((view == null) ? null : view.getVideoSurface().getHolder());
		} catch (IllegalStateException | IllegalArgumentException ex) {
			Log.e(ex, "Failed to set display");
		}
	}

	@Override
	public float getVideoWidth() {
		return player.getVideoWidth();
	}

	@Override
	public float getVideoHeight() {
		return player.getVideoHeight();
	}

	@NonNull
	@Override
	public AudioEffects getAudioEffects() {
		return audioEffects;
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		try {
			var tracks = player.getTrackInfo();
			if (tracks.length == 0) return emptyList();
			var streams = new ArrayList<AudioStreamInfo>(tracks.length);
			for (int i = 0; i < tracks.length; i++) {
				var t = tracks[i];
				if (t.getTrackType() != MEDIA_TRACK_TYPE_AUDIO) continue;
				streams.add(new AudioStreamInfo(i, t.getLanguage(), null));
			}
			return streams;
		} catch (Exception ex) {
			Log.e(ex, "Failed get audio tracks");
			return emptyList();
		}
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		try {
			var id = player.getSelectedTrack(MEDIA_TRACK_TYPE_AUDIO);
			if (id < 0) return null;
			var t = player.getTrackInfo()[id];
			return new AudioStreamInfo(id, t.getLanguage(), null);
		} catch (Exception ex) {
			Log.e(ex, "Failed get selected audio stream");
			return null;
		}
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo i) {
		try {
			if (i != null) player.selectTrack((int) i.getId());
		} catch (Exception ex) {
			Log.e(ex, "Failed selected audio stream: ", i);
		}
	}

	@Override
	public void close() {
		super.close();

		try {
			if (player.isPlaying()) player.stop();
		} catch (IllegalStateException ignore) {
		}

		if (audioEffects != null) audioEffects.release();
		player.release();
		source = null;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		if (source == null) return;
		long off = source.getOffset();
		if (off > 0) player.seekTo((int) off);
		listener.onEnginePrepared(this);
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		stopped(false);
		player.reset();
		listener.onEngineEnded(this);
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		listener.onVideoSizeChanged(this, width, height);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		MediaEngineException err;

		switch (extra) {
			case MediaPlayer.MEDIA_ERROR_IO:
				err = new MediaEngineException("MEDIA_ERROR_IO");
				break;
			case MediaPlayer.MEDIA_ERROR_MALFORMED:
				err = new MediaEngineException("MEDIA_ERROR_MALFORMED");
				break;
			case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
				err = new MediaEngineException("MEDIA_ERROR_UNSUPPORTED");
				break;
			case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
				err = new MediaEngineException("MEDIA_ERROR_TIMED_OUT");
				break;
			default:
				if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
					err = new MediaEngineException("MEDIA_ERROR_SERVER_DIED");
				} else if ((what == MediaPlayer.MEDIA_ERROR_UNKNOWN) || !player.isPlaying()) {
					err = new MediaEngineException("MEDIA_ERROR_UNKNOWN");
				} else {
					return true;
				}
		}

		listener.onEngineError(this, err);
		return true;
	}
}
