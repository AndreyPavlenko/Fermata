package me.aap.fermata.engine.exoplayer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
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
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executors;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.SubGenAddon;
import me.aap.fermata.addon.TranslateAddon;
import me.aap.fermata.addon.TranslateAddon.Translator;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;

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
			{
				setEnableDecoderFallback(true);
				setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
			}

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
		if (this.source == null) stopped(false);
		else stop();
		this.source = source;
		accessor.sourceChanged(source);
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
		accessor.sourceChanged(null);
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

	@Override
	protected FutureSupplier<Long> getSubtitlePosition() {
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
		if (BuildConfig.AUTO && !src.isVideo() && (listener instanceof MediaSessionCallback cb)) {
			addSubtitleConsumer(cb);
		}
		return completedVoid();
	}

	@Override
	public FutureSupplier<SubGrid> getCurrentSubtitles() {
		var cur = super.getCurrentSubtitles();
		if (cur != NO_SUBTITLES) return cur;
		var src = getSource();
		if (src == null) return cur;
		var ps = src.getPrefs();
		if (!ps.getBooleanPref(SubGenAddon.ENABLED)) return cur;
		setCurrentSubtitleStream(new SubtitleStreamInfo.Generated(ps.getStringPref(SubGenAddon.LANG)));
		return super.getCurrentSubtitles();
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
	public List<AudioStreamInfo> getAudioStreamInfo() {
		var groups = player.getCurrentTracks().getGroups();
		var streams = new ArrayList<AudioStreamInfo>();
		for (int i = 0, n = groups.size(); i < n; i++) {
			var group = groups.get(i);
			if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
			for (int j = 0; j < group.length; j++) {
				var fmt = group.getTrackFormat(j);
				streams.add(new AudioStreamInfo(i * 1000L + j, fmt.language, fmt.label));
			}
		}
		return streams;
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		var groups = player.getCurrentTracks().getGroups();
		for (int i = 0, n = groups.size(); i < n; i++) {
			var group = groups.get(i);
			if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
			for (int j = 0; j < group.length; j++) {
				if (group.isTrackSelected(j)) {
					var fmt = group.getTrackFormat(j);
					return new AudioStreamInfo(i * 1000L + j, fmt.language, fmt.label);
				}
			}
		}
		return null;
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo info) {
		if (info == null) return;

		var groups = player.getCurrentTracks().getGroups();
		for (int i = 0, n = groups.size(); i < n; i++) {
			var group = groups.get(i);
			if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
			for (int j = 0; j < group.length; j++) {
				if (info.getId() != (i * 1000L + j)) continue;
				player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
						.setOverrideForType(new androidx.media3.common.TrackSelectionOverride(
								group.getMediaTrackGroup(), j)).build());
				return;
			}
		}
	}

	@Override
	public void close() {
		stop();
		super.close();
		drainBuffer = null;
		accessor.player = null;
		player.removeListener(this);
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
				var prefs = source.getPrefs();
				MediaEngine.selectMediaStream(prefs::getAudioIdPref, prefs::getAudioLangPref,
						prefs::getAudioKeyPref, () -> completed(getAudioStreamInfo()),
						this::setCurrentAudioStream);
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
	protected SubGrid createSubStreamGrid() {
		return accessor.createSubStreamGrid();
	}

	static class Accessor {
		private volatile ExoPlayerEngine player;
		private volatile long subGenTimeOffset;
		private Subtitles.Stream subStream;
		private Subtitles.Stream subTransStream;
		private String transLang;
		private FutureSupplier<Translator> translator = completedNull();
		private boolean useBatchTranslate = true;

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

		private void sourceChanged(PlayableItem src) {
			if (subStream != null) subStream.clear();
			if (subTransStream != null) subTransStream.clear();

			if (src == null) {
				transLang = null;
				translator = completedNull();
				return;
			}

			var ps = src.getPrefs();
			var lang = ps.getBooleanPref(SubGenAddon.TRANSLATE) ?
					ps.getStringPref(SubGenAddon.TRANSLATE_LANG) : null;
			if (lang == null || !lang.equals(transLang)) {
				transLang = lang;
				translator = completedNull();
			}
		}

		void addSubtitles(String lang, List<Subtitles.Text> subs) {
			if (subs.isEmpty()) return;
			App.get().run(() -> {
				if (subStream == null) subStream = new Subtitles.Stream();
				var added = subStream.add(subs);
				if (transLang == null) return;

				var targetLang = transLang;
				if (translator.isDone() && translator.peek() == null) {
					translator = TranslateAddon.get().then(a -> {
						if (a == null || !targetLang.equals(transLang)) return completedNull();
						return a.getTranslator(lang, transLang);
					});
				}
				translator.main().onSuccess(tr -> {
					if (tr == null || !targetLang.equals(transLang)) return;
					if (useBatchTranslate) batchTranslate(tr, targetLang, added);
					else perItemTranslate(tr, targetLang, added);
				});
			});
		}

		private void batchTranslate(Translator tr, String targetLang, List<Subtitles.Text> subs) {
			assertMainThread();
			boolean prependPrev = false;
			if (!subStream.isEmpty()) {
				var last = subStream.get(subStream.size() - 1).getText().trim();
				var lastChar = last.isEmpty() ? '\0' : last.charAt(last.length() - 1);
				prependPrev = lastChar != '.' && lastChar != ',' && lastChar != '!' && lastChar != '?';
			}

			String concat;
			try (var tb = SharedTextBuilder.get()) {
				if (prependPrev) tb.append(subStream.get(subStream.size() - 1).getText()).append("|");
				for (var t : subs) tb.append(t.getText()).append("|");
				tb.setLength(tb.length() - 1);
				concat = tb.toString();
			}

			boolean skipFirst = prependPrev;
			Log.d("Translating: ", concat);
			tr.translate(concat).onCompletion((r, err) -> {
				if (err != null) {
					Log.e(err);
					return;
				}

				Log.d("Translation: ", r);
				String[] parts = r.split("\\|", -1);
				int off = skipFirst ? 1 : 0;

				if (subs.size() != parts.length - off) {
					Log.d("Fall back to per item translation");
					useBatchTranslate = false;
					perItemTranslate(tr, targetLang, subs);
					return;
				}

				var translated = new ArrayList<Subtitles.Text>(subs.size());
				for (int i = 0, n = subs.size(); i < n; i++) {
					var t = subs.get(i);
					t.setTranslation(parts[off + i].trim());
					translated.add(new Subtitles.Text(t.getTranslation(), t.getTime(), t.getDuration()));
				}
				App.get().run(() -> {
					if (!targetLang.equals(transLang)) return;
					if (subTransStream == null) subTransStream = new Subtitles.Stream();
					subTransStream.add(translated);
				});
			});
		}

		private void perItemTranslate(Translator tr, String targetLang, List<Subtitles.Text> subs) {
			for (var t : subs) {
				tr.translate(t.getText()).onCompletion((r, err) -> {
					if (err != null) {
						Log.e(err);
						return;
					}
					t.setTranslation(r.trim());
					var translated = new Subtitles.Text(t.getTranslation(), t.getTime(), t.getDuration());
					App.get().run(() -> {
						if (!targetLang.equals(transLang)) return;
						if (subTransStream == null) subTransStream = new Subtitles.Stream();
						subTransStream.add(translated);
					});
				});
			}
		}

		private void setSubGenTimeOffset(ExoPlayerEngine eng) {
			this.subGenTimeOffset = eng.subSchedulerClock();
		}

		private SubGrid createSubStreamGrid() {
			assertMainThread();
			if (subStream == null) subStream = new Subtitles.Stream();
			if (transLang == null) return new SubGrid(subStream);
			if (subTransStream == null) subTransStream = new Subtitles.Stream();
			var m = new EnumMap<SubGrid.Position, Subtitles>(SubGrid.Position.class);
			m.put(SubGrid.Position.BOTTOM_LEFT, subStream);
			m.put(SubGrid.Position.BOTTOM_RIGHT, subTransStream);
			return new SubGrid(m);
		}
	}
}
