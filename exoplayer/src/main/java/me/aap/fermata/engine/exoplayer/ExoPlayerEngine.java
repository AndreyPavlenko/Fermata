package me.aap.fermata.engine.exoplayer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;

import static com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;

/**
 * @author Andrey Pavlenko
 */
public class ExoPlayerEngine implements MediaEngine, Player.EventListener, AnalyticsListener {
	private final Listener listener;
	private final SimpleExoPlayer player;
	private final ProgressiveMediaSource.Factory extractor;
	private AudioEffects audioEffects;
	private PlayableItem source;
	private byte preparingState;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		this.listener = listener;
		player = new SimpleExoPlayer.Builder(ctx, new DefaultRenderersFactory(ctx)
				.setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)).build();
		player.addListener(this);
		player.addAnalyticsListener(this);
		DataSource.Factory f = new DefaultDataSourceFactory(ctx, "Fermata");
		extractor = new ProgressiveMediaSource.Factory(f);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_EXO;
	}

	@Override
	public void prepare(PlayableItem source) {
		this.source = source;
		preparingState = 1;
		player.prepare(extractor.createMediaSource(source.getLocation()), false, false);
	}

	@Override
	public void start() {
		player.setPlayWhenReady(true);
	}

	@Override
	public void stop() {
		player.stop();
		source = null;
	}

	@Override
	public void pause() {
		player.setPlayWhenReady(false);
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public long getDuration() {
		return (player == null) ? 0 : player.getDuration();
	}

	@Override
	public long getPosition() {
		return (source != null) ? (player.getCurrentPosition() - source.getOffset()) : 0;
	}

	@Override
	public void setPosition(long position) {
		if (source != null) player.seekTo(source.getOffset() + position);
	}

	@Override
	public float getSpeed() {
		return player.getPlaybackParameters().speed;
	}

	@Override
	public void setSpeed(float speed) {
		player.setPlaybackParameters(new PlaybackParameters(speed));
	}

	@Override
	public void setVideoView(VideoView view) {
		player.setVideoSurfaceHolder((view == null) ? null : view.getVideoSurface().getHolder());
	}

	@Override
	public float getVideoWidth() {
		Format f = player.getVideoFormat();
		return (f == null) ? 0 : f.width;
	}

	@Override
	public float getVideoHeight() {
		Format f = player.getVideoFormat();
		return (f == null) ? 0 : f.height;
	}

	@Override
	public AudioEffects getAudioEffects() {
		return audioEffects;
	}

	@Override
	public void close() {
		stop();
		player.release();
		source = null;
	}

	@Override
	public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
		if (playbackState == Player.STATE_READY) {
			if (preparingState == 1) {
				if ((audioEffects != null)) {
					preparingState = 0;
					listener.onEnginePrepared(this);
				} else {
					preparingState = 2;
				}
			}
		} else if (playbackState == Player.STATE_ENDED) {
			listener.onEngineEnded(this);
		}
	}

	@Override
	public void onAudioSessionId(@NonNull EventTime eventTime, int audioSessionId) {
		try {
			audioEffects = AudioEffects.create(0, audioSessionId);
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to create audio effects", ex);
		}

		if (preparingState == 2) {
			preparingState = 0;
			listener.onEnginePrepared(this);
		}
	}

	@Override
	public void onVideoSizeChanged(@NonNull EventTime eventTime, int width, int height,
																 int unappliedRotationDegrees, float pixelWidthHeightRatio) {
		listener.onVideoSizeChanged(this, width, height);
	}

	@Override
	public void onPlayerError(@NonNull ExoPlaybackException error) {
		listener.onEngineError(this, error);
	}
}
