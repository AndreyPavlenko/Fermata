package me.aap.fermata.engine.exoplayer;

import static com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
import static me.aap.utils.async.Completed.completed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class ExoPlayerEngine extends MediaEngineBase implements Player.Listener {
	private final Context ctx;
	private final ExoPlayer player;
	private final AudioEffects audioEffects;
	private final DataSource.Factory dsFactory;
	private ProgressiveMediaSource.Factory progressive;
	private HlsMediaSource.Factory hls;
	private PlayableItem source;
	private boolean preparing;
	private boolean buffering;
	private boolean isHls;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		super(listener);
		this.ctx = ctx;
		player = new ExoPlayer.Builder(ctx, new DefaultRenderersFactory(ctx).setExtensionRendererMode(
				EXTENSION_RENDERER_MODE_PREFER)).build();
		player.addListener(this);
		audioEffects = AudioEffects.create(0, player.getAudioSessionId());
		dsFactory = new DefaultDataSourceFactory(ctx, "Fermata/" + BuildConfig.VERSION_NAME);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_EXO;
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void prepare(PlayableItem source) {
		stopped(false);
		this.source = source;
		preparing = true;
		buffering = false;

		Uri uri = source.getLocation();
		MediaItem m = MediaItem.fromUri(uri);
		int type = Util.inferContentType(uri, null);

		switch (type) {
			case C.TYPE_HLS -> {
				if (hls == null) hls = new HlsMediaSource.Factory(getDsFactory(source));
				isHls = true;
				player.setMediaSource(hls.createMediaSource(m), true);
			}
			case C.TYPE_OTHER -> {
				if (progressive == null)
					progressive = new ProgressiveMediaSource.Factory(getDsFactory(source));
				isHls = false;
				player.setMediaSource(progressive.createMediaSource(m), true);
			}
			default ->
					listener.onEngineError(this, new IllegalArgumentException("Unsupported type: " + type));
		}

		player.prepare();
	}

	private DataSource.Factory getDsFactory(PlayableItem source) {
		String agent = source.getUserAgent();
		return (agent == null) ? dsFactory : new DefaultDataSourceFactory(ctx, agent);
	}

	@Override
	public void start() {
		player.setPlayWhenReady(true);
		listener.onEngineStarted(this);
		started();
	}

	@Override
	public void stop() {
		stopped(false);
		player.stop();
		source = null;
	}

	@Override
	public void pause() {
		stopped(true);
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
		long pos = pos();
		syncSub(pos, speed(), false);
		return completed(pos);
	}

	private long pos() {
		return !isHls && (source != null) ? (player.getCurrentPosition() - source.getOffset()) : 0;
	}

	@Override
	public void setPosition(long position) {
		if (source == null) return;
		long pos = source.getOffset() + position;
		player.seekTo(pos);
		syncSub(pos, speed(), true);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(speed());
	}

	private float speed() {
		return player.getPlaybackParameters().speed;
	}

	@Override
	public void setSpeed(float speed) {
		player.setPlaybackParameters(new PlaybackParameters(speed));
		syncSub(pos(), speed, true);
	}

	@Override
	public void setVideoView(VideoView view) {
		super.setVideoView(view);
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
		super.close();
		player.release();
		source = null;
		if (audioEffects != null) audioEffects.release();
	}

	@Override
	public void onPlaybackStateChanged(int playbackState) {
		if (playbackState == Player.STATE_BUFFERING) {
			buffering = true;
			listener.onEngineBuffering(this, player.getBufferedPercentage());
		} else if (playbackState == Player.STATE_READY) {
			if (buffering) {
				buffering = false;
				listener.onEngineBufferingCompleted(this);
			}
			if (preparing) {
				preparing = false;
				long off = source.getOffset();
				if (off > 0) player.seekTo(off);
				listener.onEnginePrepared(this);
			}
		} else if (playbackState == Player.STATE_ENDED) {
			stopped(false);
			listener.onEngineEnded(this);
		}
	}

	@Override
	public void onVideoSizeChanged(VideoSize videoSize) {
		listener.onVideoSizeChanged(this, videoSize.width, videoSize.height);
	}

	@Override
	public void onPlayerError(PlaybackException error) {
		listener.onEngineError(this, error);
	}
}
