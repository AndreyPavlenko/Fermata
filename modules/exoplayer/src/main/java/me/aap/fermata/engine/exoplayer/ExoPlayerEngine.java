package me.aap.fermata.engine.exoplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;

import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;

import static com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
import static me.aap.utils.async.Completed.completed;

/**
 * @author Andrey Pavlenko
 */
public class ExoPlayerEngine implements MediaEngine, Player.EventListener, VideoListener {
	private final Listener listener;
	private final SimpleExoPlayer player;
	private final AudioEffects audioEffects;
	private final DataSource.Factory dsFactory;
	private ProgressiveMediaSource.Factory progressive;
	private HlsMediaSource.Factory hls;
	private PlayableItem source;
	private boolean preparing;
	private boolean isHls;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		this.listener = listener;
		player = new SimpleExoPlayer.Builder(ctx, new DefaultRenderersFactory(ctx)
				.setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)).build();
		player.addListener(this);
		player.addVideoListener(this);
		audioEffects = AudioEffects.create(0, player.getAudioSessionId());
		dsFactory = new DefaultDataSourceFactory(ctx, "Fermata");
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_EXO;
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void prepare(PlayableItem source) {
		this.source = source;
		preparing = true;

		Uri uri = source.getLocation();
		MediaItem m = MediaItem.fromUri(uri);
		int type = Util.inferContentType(uri, null);

		switch (type) {
			case C.TYPE_HLS:
				if (hls == null) hls = new HlsMediaSource.Factory(dsFactory);
				isHls = true;
				player.setMediaSource(hls.createMediaSource(m), false);
				break;
			case C.TYPE_OTHER:
				if (progressive == null) progressive = new ProgressiveMediaSource.Factory(dsFactory);
				isHls = false;
				player.setMediaSource(progressive.createMediaSource(m), false);
				break;
			default:
				listener.onEngineError(this, new IllegalArgumentException("Unsupported type: " + type));
		}

		player.prepare();
	}

	@Override
	public void start() {
		player.setPlayWhenReady(true);
		listener.onEngineStarted(this);
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
	public FutureSupplier<Long> getDuration() {
		return completed(!isHls && (source != null) ? player.getDuration() : 0);
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return completed(!isHls && (source != null) ? (player.getCurrentPosition() - source.getOffset()) : 0);
	}

	@Override
	public void setPosition(long position) {
		if (source != null) player.seekTo(source.getOffset() + position);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(player.getPlaybackParameters().speed);
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

		if (audioEffects != null) {
			audioEffects.release();
		}
	}

	@Override
	public void onPlaybackStateChanged(int playbackState) {
		if (playbackState == Player.STATE_READY) {
			if (preparing) {
				preparing = false;
				listener.onEnginePrepared(this);
			}
		} else if (playbackState == Player.STATE_ENDED) {
			listener.onEngineEnded(this);
		}
	}

	@Override
	public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
		listener.onVideoSizeChanged(this, width, height);
	}

	@Override
	public void onPlayerError(@NonNull ExoPlaybackException error) {
		listener.onEngineError(this, error);
	}
}
