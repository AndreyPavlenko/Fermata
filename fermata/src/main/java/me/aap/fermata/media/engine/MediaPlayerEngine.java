package me.aap.fermata.media.engine;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

import static me.aap.utils.async.Completed.completed;

/**
 * @author Andrey Pavlenko
 */
public class MediaPlayerEngine implements MediaEngine,
		MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
		MediaPlayer.OnVideoSizeChangedListener,
		MediaPlayer.OnErrorListener {
	private final Context ctx;
	private final Listener listener;
	private final MediaPlayer player;
	private final AudioEffects audioEffects;
	private PlayableItem source;

	public MediaPlayerEngine(Context ctx, Listener listener) {
		this.ctx = ctx;
		this.listener = listener;
		player = new MediaPlayer();
		int sessionId = player.getAudioSessionId();
		audioEffects = AudioEffects.create(0, sessionId);
		AudioAttributes attrs = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
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
		this.source = source;

		try {
			player.reset();
			player.setDataSource(source.getLocation().toString());
			player.prepareAsync();
		} catch (Exception ex) {
			listener.onEngineError(this, ex);
			this.source = null;
		}
	}

	@Override
	public void start() {
		player.start();
		listener.onEngineStarted(this);
	}

	@Override
	public void stop() {
		player.stop();
		player.reset();
		source = null;
	}

	@Override
	public void pause() {
		player.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return completed((source == null) || source.isStream() ? 0L : player.getDuration());
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return completed((source != null) ? (player.getCurrentPosition() - source.getOffset()) : 0);
	}

	@Override
	public void setPosition(long position) {
		if (source != null) player.seekTo((int) (source.getOffset() + position));
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		try {
			return completed(player.getPlaybackParams().getSpeed());
		} catch (IllegalStateException ex) {
			Log.d(ex);
			return completed(1f);
		}
	}

	@Override
	public void setSpeed(float speed) {
		try {
			PlaybackParams p = player.getPlaybackParams();
			p.setSpeed(speed);
			player.setPlaybackParams(p);
		} catch (Exception ex) {
			Log.e(ex, "Failed to set speed: ", speed);
		}
	}

	@Override
	public void setVideoView(VideoView view) {
		try {
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
	public void close() {
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
		listener.onEnginePrepared(this);
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
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
