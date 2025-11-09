package me.aap.fermata.engine.exoplayer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.misc.Assert.assertMainThread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cronet.CronetDataSource;
import androidx.media3.datasource.cronet.CronetUtil;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import org.chromium.net.CronetEngine;

import java.lang.reflect.Field;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.SubGenAddon;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

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

	private final Accessor accessor = new Accessor(this);
	private final Timeline.Period period = new Timeline.Period();
	private final PendingLoadAudioProcessor audioProc = new PendingLoadAudioProcessor(accessor);
	private final ExoPlayer player;
	private final AudioEffects audioEffects;
	private volatile PlayableItem source;
	private boolean preparing;
	private boolean buffering;
	private boolean isHls;
	private Runnable drainBuffer;

	public ExoPlayerEngine(Context ctx, Listener listener) {
		super(listener);
		DefaultDataSource.Factory dsFactory = new DefaultDataSource.Factory(ctx, httpDsFactory);
		MediaSource.Factory msFactory =
				new DefaultMediaSourceFactory(ctx).setDataSourceFactory(dsFactory);
		player = new ExoPlayer.Builder(ctx, new DefaultRenderersFactory(ctx) {
			@Override
			protected AudioSink buildAudioSink(@NonNull Context context,
																				 boolean enableFloatOutput,
																				 boolean enableAudioTrackPlaybackParams) {
				return new DefaultAudioSink.Builder(ctx)
						.setAudioTrackBufferSizeProvider(new DefaultAudioTrackBufferSizeProvider.Builder()
								.setMaxPcmBufferDurationUs(5000_000)
								.setPcmBufferMultiplicationFactor(16)
								.setOffloadBufferDurationUs(120_000_000).build())
						.setAudioProcessorChain(
								new DefaultAudioSink.DefaultAudioProcessorChain(audioProc)).build();
			}
		}).setMediaSourceFactory(msFactory).build();
		player.addListener(this);
		audioEffects = AudioEffects.create(0, player.getAudioSessionId());

		try {
			Field f = player.getClass().getDeclaredField("internalPlayer");
			f.setAccessible(true);
			Object internal = requireNonNull(f.get(player));
			f = internal.getClass().getDeclaredField("handler");
			f.setAccessible(true);
			var handler = (HandlerWrapper) requireNonNull(f.get(internal));
			drainBuffer = () -> {
				try {
					handler.sendEmptyMessage(2 /*MSG_DO_SOME_WORK*/);
				} catch (Exception err) {
					Log.w(err);
				}
			};
		} catch (Exception err) {
			Log.w(err);
		}
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
		syncSub(false);
		return completed(pos());
	}

	private long pos() {
		if (source == null) return 0L;
		var pos = player.getCurrentPosition();
		if (isHls) {
			var tl = player.getCurrentTimeline();
			if (!tl.isEmpty()) {
				pos -= tl.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs();
			}
		}
		return pos - source.getOffset();
	}

	protected long subSchedulerClock() {
		return pos();
	}

	void syncSub(boolean restart) {
		syncSub(subSchedulerClock(), speed(), restart);
	}

	@Override
	public void setPosition(long position) {
		if (source == null) return;
		var pos = source.getOffset() + position;
		player.seekTo(pos);
		accessor.setSubGenTimeOffset(this);
		syncSub(true);
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
		syncSub(true);
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
	public FutureSupplier<Void> selectSubtitleStream() {
		var src = getSource();
		if (src == null) return completedVoid();
		var ps = src.getPrefs();
		if (!ps.getBooleanPref(SubGenAddon.ENABLED)) return super.selectSubtitleStream();
		setCurrentSubtitleStream(new SubtitleStreamInfo.Generated(ps.getStringPref(SubGenAddon.LANG)));
		return completedVoid();
	}

	@Override
	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		return super.getSubtitleStreamInfo().main().map(subFiles -> {
			var src = getSource();
			if (src == null) return emptyList();
			var ps = src.getPrefs();
			if (ps.getBooleanPref(SubGenAddon.ENABLED)) {
				var streams = new ArrayList<SubtitleStreamInfo>(subFiles.size() + 1);
				streams.add(new SubtitleStreamInfo.Generated(ps.getStringPref(SubGenAddon.LANG)));
				streams.addAll(subFiles);
				return streams;
			}
			return subFiles;
		});
	}

	@Override
	public void close() {
		stop();
		super.close();
		drainBuffer = null;
		accessor.player = null;
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
				accessor.setSubGenTimeOffset(this);
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

	@Override
	protected Subtitles.Stream createSubStream() {
		return accessor.getSubStream();
	}

	static class Accessor {
		private volatile ExoPlayerEngine player;
		private volatile long subGenTimeOffset;
		private volatile Subtitles.Stream subStream;

		private Accessor(ExoPlayerEngine player) {
			this.player = player;
		}

		void drainBuffer() {
			assertMainThread();
			if (player == null) return;
			if (player.drainBuffer != null) player.drainBuffer.run();
			player.syncSub(false);
		}

		@Nullable
		public PlayableItem getSource() {
			var p = player;
			return p == null ? null : p.source;
		}

		public long getSubGenTimeOffset() {
			return subGenTimeOffset;
		}

		private void setSubGenTimeOffset(ExoPlayerEngine eng) {
			this.subGenTimeOffset = eng.subSchedulerClock();
		}

		@NonNull
		public Subtitles.Stream getSubStream() {
			var s = subStream;
			if (s == null) {
				synchronized (this) {
					s = subStream;
					if (s == null) subStream = s = new Subtitles.Stream();
				}
			}
			return s;
		}
	}
}
