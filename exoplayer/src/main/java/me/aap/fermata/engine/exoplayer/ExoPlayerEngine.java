package me.aap.fermata.engine.exoplayer;

import android.content.Context;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public class ExoPlayerEngine implements MediaEngine, Player.EventListener {
	public static final String ID = "ExoPlayerEngine";
	private final SimpleExoPlayer player;
	private final Listener listener;
	private final ProgressiveMediaSource.Factory extractor;
	private PlayableItem source;
	private boolean preparing;
	private boolean buffering;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		this.listener = listener;
		player = ExoPlayerFactory.newSimpleInstance(ctx);
		player.addListener(this);
		DataSource.Factory f = new DefaultDataSourceFactory(ctx, "Fermata");
		extractor = new ProgressiveMediaSource.Factory(f);
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public void prepare(PlayableItem source) {
		this.source = source;
		preparing = true;
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
	public void setSurface(SurfaceHolder surface) {
		player.setVideoSurfaceHolder(surface);
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
	public boolean canPlay(PlayableItem i) {
		return true;
	}

	@Override
	public void close() {
		stop();
		player.release();
		source = null;
	}

	@Override
	public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
		switch (playbackState) {
			case Player.STATE_BUFFERING:
				buffering = true;
				listener.onEngineBuffering(this, player.getBufferedPercentage());
				break;
			case Player.STATE_READY:
				if (preparing) {
					listener.onEnginePrepared(this);
					preparing = false;
				} else if (buffering) {
					buffering = false;
					listener.onEngineBufferingCompleted(this);
				}

				break;
			case Player.STATE_ENDED:
				listener.onEngineEnded(this);
				break;
		}
	}

	@Override
	public void onPlayerError(ExoPlaybackException error) {
		listener.onEngineError(this, error);
	}
}
