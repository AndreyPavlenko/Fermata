package me.aap.fermata.engine.exoplayer;

import static me.aap.utils.async.Completed.completed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cronet.CronetDataSource;
import androidx.media3.datasource.cronet.CronetUtil;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import org.chromium.net.CronetEngine;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.Executors;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
@UnstableApi
public class ExoPlayerEngine extends MediaEngineBase implements Player.Listener {
	private static final DataSource.Factory httpDsFactory;

	static {
		CronetEngine cre = CronetUtil.buildCronetEngine(FermataApplication.get(),
				"Fermata/" + BuildConfig.VERSION_NAME, true);
		if (cre != null) {
			httpDsFactory = new CronetDataSource.Factory(cre, Executors.newSingleThreadExecutor());
		} else {
			CookieManager cookieManager = new CookieManager();
			cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
			CookieHandler.setDefault(cookieManager);
			httpDsFactory = new DefaultHttpDataSource.Factory();
		}
	}

	private final ExoPlayer player;
	private final AudioEffects audioEffects;
	private PlayableItem source;
	private boolean preparing;
	private boolean buffering;
	private boolean isHls;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		super(listener);
		DefaultDataSource.Factory dsFactory = new DefaultDataSource.Factory(ctx, httpDsFactory);
		MediaSource.Factory msFactory =
				new DefaultMediaSourceFactory(ctx).setDataSourceFactory(dsFactory);
		player = new ExoPlayer.Builder(ctx).setMediaSourceFactory(msFactory).build();
		player.addListener(this);
		audioEffects = AudioEffects.create(0, player.getAudioSessionId());
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
		isHls = Util.inferContentType(uri) == C.CONTENT_TYPE_HLS;
		player.setMediaItem(m);
		player.prepare();
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
	public void mute(Context ctx) {
		player.setVolume(0f);
	}

	@Override
	public void unmute(Context ctx) {
		player.setVolume(1f);
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
	public void onPlayerError(@NonNull PlaybackException error) {
		listener.onEngineError(this, error);
	}
}
