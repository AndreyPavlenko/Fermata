package me.aap.fermata.engine.vlc;

import android.content.ContentResolver;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.MediaPlayer.TrackDescription;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IMedia.AudioTrack;
import org.videolan.libvlc.interfaces.IMedia.SubtitleTrack;
import org.videolan.libvlc.interfaces.IMedia.VideoTrack;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineException;
import me.aap.fermata.media.engine.MediaStreamInfo;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.IoUtils;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class VlcEngine implements MediaEngine, MediaPlayer.EventListener, SurfaceHolder.Callback,
		IVLCVout.OnNewVideoLayoutListener {
	@SuppressWarnings({"FieldCanBeLocal", "unused"}) // Hold reference to prevent garbage collection
	private final VlcEngineProvider provider;
	private final LibVLC vlc;
	private final MediaPlayer player;
	private final AudioEffects effects;
	private final Listener listener;
	@NonNull
	private Source source = Source.NULL;
	private VideoView videoView;
	private boolean playing;
	private long pendingPosition = -1;

	public VlcEngine(VlcEngineProvider provider, Listener listener) {
		LibVLC vlc = provider.getVlc();
		int sessionId = provider.getAudioSessionId();
		effects = (sessionId != AudioManager.ERROR) ? AudioEffects.create(0, sessionId) : null;
		this.provider = provider;
		this.vlc = vlc;
		this.listener = listener;
		player = new MediaPlayer(vlc);
		player.setEventListener(this);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_VLC;
	}

	@Override
	public void prepare(PlayableItem source) {
		this.source.close();
		this.source = Source.NULL;
		Media media = null;
		ParcelFileDescriptor fd = null;

		try {
			Uri uri = source.getLocation();

			if ("content".equals(uri.getScheme())) {
				ContentResolver cr = vlc.getAppContext().getContentResolver();
				fd = cr.openFileDescriptor(uri, "r");
				media = (fd != null) ? new Media(vlc, fd.getFileDescriptor()) : new Media(vlc, uri);
			} else {
				media = new Media(vlc, uri);
			}

			PendingSource pending = new PendingSource(source, media, fd);
			this.source = pending;
			media.addOption(":input-fast-seek");

			if (media.isParsed()) {
				prepared(pending);
			} else {
				Media m = media;
				m.setEventListener(e -> {
					if (m.isParsed()) {
						m.setEventListener(null);
						prepared(pending);
					}
				});
				m.parseAsync();
			}
		} catch (Throwable ex) {
			IoUtils.close(fd);
			if (media != null) media.release();
			if (this.source == Source.NULL) this.source = new Source(source, null);
			else this.source.close();
			listener.onEngineError(this, ex);
		}
	}

	private void prepared(PendingSource source) {
		if (source != this.source) {
			source.close();
			return;
		}

		Media media = source.getMedia();
		this.source = source.prepare();
		playing = false;
		pendingPosition = -1;
		player.setMedia(media);
		source.release();
		listener.onEnginePrepared(this);
	}

	@Override
	public void start() {
		player.play();
	}

	@Override
	public void stop() {
		playing = false;
		pendingPosition = -1;
		player.stop();
		player.detachViews();
		source.close();
		source = Source.NULL;
	}

	@Override
	public void pause() {
		player.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source.getItem();
	}

	@Override
	public long getDuration() {
		long dur = source.getDuration();

		if (dur <= 0) {
			if ((dur = player.getLength()) > 0) {
				source.setDuration(dur);
				return dur;
			} else {
				return 0;
			}
		}

		return dur;
	}

	@Override
	public long getPosition() {
		Source src = source;

		if (src != Source.NULL) {
			return (pendingPosition == -1) ? (player.getTime() - src.getItem().getOffset()) : pendingPosition;
		} else {
			return 0;
		}
	}

	@Override
	public void setPosition(long position) {
		Source src = source;

		if (src != Source.NULL) {
			if (playing) player.setTime(src.getItem().getOffset() + position);
			else pendingPosition = position;
		}
	}

	@Override
	public float getSpeed() {
		return player.getRate();
	}

	@Override
	public void setSpeed(float speed) {
		player.setRate(speed);
	}

	@Override
	public void setVideoView(VideoView view) {
		if (this.videoView != null) {
			this.videoView.getVideoSurface().getHolder().removeCallback(this);
		}

		this.videoView = view;
		IVLCVout out = player.getVLCVout();
		out.detachViews();

		if (view != null) {
			SurfaceHolder surface = view.getVideoSurface().getHolder();
			Rect size = surface.getSurfaceFrame();
			surface.addCallback(this);
			out.setVideoView(view.getVideoSurface());
			out.setSubtitlesView(view.getSubtitleSurface(true));
			out.attachViews(this);
			out.setWindowSize(size.width(), size.height());
			player.setAspectRatio(size.width() + ":" + size.height());
		}
	}

	@Override
	public float getVideoWidth() {
		return source.getVideoWidth();
	}

	@Override
	public float getVideoHeight() {
		return source.getVideoHeight();
	}

	@Override
	public AudioEffects getAudioEffects() {
		return effects;
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		return source.getAudioStreamInfo();
	}

	@Override
	public List<SubtitleStreamInfo> getSubtitleStreamInfo() {
		return source.getSubtitleStreamInfo();
	}

	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		int id = player.getAudioTrack();
		return CollectionUtils.find(getAudioStreamInfo(), s -> s.getId() == id);
	}

	@Override
	public void setCurrentAudioStream(AudioStreamInfo i) {
		player.setAudioTrack((i != null) ? i.getId() : -1);
	}

	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		int id = player.getSpuTrack();
		return CollectionUtils.find(getSubtitleStreamInfo(), s -> s.getId() == id);
	}

	@Override
	public void setCurrentSubtitleStream(SubtitleStreamInfo i) {
		player.setSpuTrack((i != null) ? i.getId() : -1);
	}

	@Override
	public boolean isAudioDelaySupported() {
		return true;
	}

	@Override
	public int getAudioDelay() {
		return (int) (player.getAudioDelay() / 1000);
	}

	@Override
	public void setAudioDelay(int milliseconds) {
		player.setAudioDelay(milliseconds * 1000);
	}

	@Override
	public boolean isSubtitleDelaySupported() {
		return true;
	}

	@Override
	public int getSubtitleDelay() {
		return (int) (player.getSpuDelay() / 1000);
	}

	@Override
	public void setSubtitleDelay(int milliseconds) {
		player.setSpuDelay(milliseconds * 1000);
	}

	@Override
	public void close() {
		stop();

		if (videoView != null) {
			videoView.getVideoSurface().getHolder().removeCallback(this);
			videoView = null;
		}

		player.release();
		if (effects != null) effects.release();
	}

	@Override
	public void onEvent(MediaPlayer.Event event) {
		switch (event.type) {
			case MediaPlayer.Event.Playing:
				startPlaying();
				break;
			case MediaPlayer.Event.EndReached:
				listener.onEngineEnded(this);
				break;
			case MediaPlayer.Event.EncounteredError:
				listener.onEngineError(this, new MediaEngineException(""));
				break;
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		try {
			player.getVLCVout().setWindowSize(width, height);
			player.setAspectRatio(width + ":" + height);
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to set window size and ratio", ex);
		}
	}

	@Override
	public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth,
															 int visibleHeight, int sarNum, int sarDen) {
		listener.onVideoSizeChanged(this, width, height);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if ((videoView != null) && (videoView.getVideoSurface().getHolder() == holder)) {
			holder.removeCallback(this);
			videoView = null;
			player.getVLCVout().detachViews();
		}
	}

	private void startPlaying() {
		playing = true;

		if (this.source instanceof VideoSource) {
			VideoSource vs = (VideoSource) this.source;
			PlayableItemPrefs prefs = vs.getItem().getPrefs();
			int delay = prefs.getAudioDelayPref();
			AudioStreamInfo ai = vs.selectAudioStream(prefs);
			if (ai != null) player.setAudioTrack(ai.getId());
			if (delay != 0) player.setAudioDelay(delay * 1000);
			vs.addSubtitles(player.getSpuTracks());

			if (prefs.getSubEnabledPref()) {
				SubtitleStreamInfo si = vs.selectSubtitleStream(prefs);

				if (si != null) {
					player.setSpuTrack(si.getId());
					delay = prefs.getSubDelayPref();
					if (delay != 0) player.setSpuDelay(delay * 1000);
				}
			} else {
				player.setSpuTrack(-1);
			}
		}

		if (pendingPosition != -1) {
			setPosition(pendingPosition);
			pendingPosition = -1;
		}
	}

	private static class Source implements Closeable {
		private static final Source NULL = new Source(null, null);
		private final PlayableItem item;
		ParcelFileDescriptor fd;

		Source(PlayableItem item, ParcelFileDescriptor fd) {
			this.item = item;
			this.fd = fd;
		}

		PlayableItem getItem() {
			return item;
		}

		long getDuration() {
			return 0;
		}

		void setDuration(long duration) {
		}

		int getVideoWidth() {
			return 0;
		}

		int getVideoHeight() {
			return 0;
		}

		List<SubtitleStreamInfo> getSubtitleStreamInfo() {
			return Collections.emptyList();
		}

		List<AudioStreamInfo> getAudioStreamInfo() {
			return Collections.emptyList();
		}

		@Override
		@CallSuper
		public void close() {
			if (fd != null) {
				IoUtils.close(fd);
				fd = null;
			}
		}

		@NonNull
		@Override
		public String toString() {
			return String.valueOf(getItem());
		}
	}

	private static class PendingSource extends Source {
		Media media;

		public PendingSource(PlayableItem item, Media media, ParcelFileDescriptor fd) {
			super(item, fd);
			this.media = media;
		}

		Media getMedia() {
			return media;
		}

		PreparedSource prepare() {
			PlayableItem pi = getItem();

			if (pi.isVideo()) {
				int w = 0, h = 0;
				ArrayList<AudioStreamInfo> audio = new ArrayList<>();
				ArrayList<SubtitleStreamInfo> subtitle = new ArrayList<>();

				for (int i = 0, n = media.getTrackCount(); i < n; i++) {
					IMedia.Track t = media.getTrack(i);

					if (t instanceof VideoTrack) {
						VideoTrack v = (VideoTrack) t;
						w = v.width;
						h = v.height;
					} else if (t instanceof AudioTrack) {
						AudioTrack a = (AudioTrack) t;
						audio.add(new AudioStreamInfo(a.id, a.language, a.description));
					} else if (t instanceof SubtitleTrack) {
						SubtitleTrack s = (SubtitleTrack) t;
						subtitle.add(new SubtitleStreamInfo(s.id, s.language, s.description));
					}
				}

				audio.trimToSize();
				subtitle.trimToSize();
				return new VideoSource(pi, fd, media.getDuration(), w, h,
						audio.isEmpty() ? Collections.emptyList() : audio,
						subtitle.isEmpty() ? Collections.emptyList() : subtitle);
			} else {
				return new PreparedSource(pi, fd, media.getDuration());
			}
		}

		public void close() {
			super.close();
			release();
		}

		void release() {
			if (media != null) {
				media.release();
				media = null;
			}
		}
	}

	private static class PreparedSource extends Source {
		private long duration;

		PreparedSource(PlayableItem item, ParcelFileDescriptor fd, long duration) {
			super(item, fd);
			this.duration = duration;
		}

		@Override
		long getDuration() {
			return duration;
		}

		@Override
		void setDuration(long duration) {
			this.duration = duration;
		}
	}

	private static final class VideoSource extends PreparedSource {
		private final int videoWidth;
		private final int videoHeight;
		private final List<AudioStreamInfo> audioStreamInfo;
		private List<SubtitleStreamInfo> subtitleStreamInfo;

		VideoSource(PlayableItem item, ParcelFileDescriptor fd, long duration, int videoWidth, int videoHeight,
								List<AudioStreamInfo> audioStreamInfo,
								List<SubtitleStreamInfo> subtitleStreamInfo) {
			super(item, fd, duration);
			this.videoWidth = videoWidth;
			this.videoHeight = videoHeight;
			this.subtitleStreamInfo = subtitleStreamInfo;
			this.audioStreamInfo = audioStreamInfo;
		}

		@Override
		int getVideoWidth() {
			return videoWidth;
		}

		@Override
		int getVideoHeight() {
			return videoHeight;
		}

		@Override
		List<AudioStreamInfo> getAudioStreamInfo() {
			return audioStreamInfo;
		}

		@Override
		List<SubtitleStreamInfo> getSubtitleStreamInfo() {
			return subtitleStreamInfo;
		}

		void addSubtitles(TrackDescription... tracks) {
			if (tracks != null) {
				for (TrackDescription t : tracks) {
					if (t.id != -1) {
						SubtitleStreamInfo info = new SubtitleStreamInfo(t.id, null, t.name);

						if (!subtitleStreamInfo.contains(info)) {
							if (subtitleStreamInfo.isEmpty()) subtitleStreamInfo = new ArrayList<>();
							subtitleStreamInfo.add(info);
						}
					}
				}
			}
		}

		AudioStreamInfo selectAudioStream(PlayableItemPrefs prefs) {
			return selectMediaStream(getAudioStreamInfo(), prefs::getAudioIdPref,
					prefs::getAudioLangPref, prefs::getAudioKeyPref);
		}

		SubtitleStreamInfo selectSubtitleStream(PlayableItemPrefs prefs) {
			return selectMediaStream(getSubtitleStreamInfo(), prefs::getSubIdPref,
					prefs::getSubLangPref, prefs::getSubKeyPref);
		}

		private static <I extends MediaStreamInfo> I selectMediaStream(List<I> streams,
																																	 Supplier<Integer> idSupplier,
																																	 Supplier<String> langSupplier,
																																	 Supplier<String> keySupplier) {
			if (streams.isEmpty()) return null;

			Integer id = idSupplier.get();

			if (id != null) {
				for (I i : streams) {
					if (id == i.getId()) return i;
				}
			}

			String lang = langSupplier.get().trim();
			boolean hasMatching = false;

			if (!lang.isEmpty()) {
				List<I> filtered = null;

				for (StringTokenizer st = new StringTokenizer(lang, ", "); st.hasMoreTokens(); ) {
					String l = st.nextToken();

					if (!l.isEmpty()) {
						for (I i : streams) {
							if (l.equalsIgnoreCase(i.getLanguage())) {
								hasMatching = true;
								if (filtered == null) filtered = new ArrayList<>(streams.size());
								if (!filtered.contains(i)) filtered.add(i);
							}
						}
					}
				}

				if (filtered != null) streams = filtered;
			}

			String key = keySupplier.get().trim();

			if (!key.isEmpty()) {
				for (StringTokenizer st = new StringTokenizer(key, ", "); st.hasMoreTokens(); ) {
					String k = st.nextToken();

					if (!k.isEmpty()) {
						k = k.toLowerCase();

						for (I i : streams) {
							String dsc = i.getDescription();
							if ((dsc != null) && TextUtils.containsWord(dsc.toLowerCase(), k)) return i;
						}
					}
				}
			}

			return hasMatching ? streams.get(0) : null;
		}
	}
}
