package me.aap.fermata.media.engine;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static java.lang.System.currentTimeMillis;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;
import static me.aap.utils.async.Completed.completed;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import java.util.List;

import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.PlayableItemWrapper;
import me.aap.fermata.ui.view.VideoInfoView;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public class StreamEngine implements MediaEngine, MediaEngine.Listener {
	private static final FutureSupplier<Float> SPEED = completed(1f);
	private final MediaEngine eng;
	private final MediaEngine.Listener listener;
	private int state = STATE_STOPPED;
	private PlayableItem source;
	private VideoView videoView;
	private long startTime;
	private long endTime;
	private long position;
	private long lag;
	private long startStamp;
	private long bufferingStamp;
	private Cancellable timer;
	private boolean positionChanged;
	@NonNull
	private FutureSupplier<Long> duration = completed(0L);

	public StreamEngine(MediaEngineProvider p, MediaEngine.Listener listener) {
		eng = p.createEngine(this);
		this.listener = listener;
	}

	@Override
	public void prepare(PlayableItem src) {
		if (src instanceof ArchiveItem a) {
			setSource(src, a.getStartTime(), a.getEndTime());
			listener.onEnginePrepared(this);
		} else {
			setSource(src, true);
		}
	}

	private FutureSupplier<MediaDescriptionCompat> setSource(PlayableItem src, boolean notify) {
		PlayableItem old = source;
		return src.getMediaDescription().main().onCompletion((md, err) -> {
			if (source != old) return;
			if (err != null) {
				setSource(src, 0, 0);
				onEngineError(eng, err);
			} else {
				Bundle b = md.getExtras();
				if (b != null) {
					setSource(src, b.getLong(STREAM_START_TIME, 0), b.getLong(STREAM_END_TIME, 0));
				} else {
					setSource(src, 0, 0);
				}
				if (notify) listener.onEnginePrepared(this);
			}
		});
	}

	private void setSource(PlayableItem src, long start, long end) {
		reset();
		source = src;
		startStamp = currentTimeMillis();
		if ((start > 0) && (start < end)) {
			startTime = start;
			endTime = end;
			duration = completed(end - start);
		}
	}

	private void updateSource() {
		assert isPlaying();
		stopTimer();
		PlayableItem src = source;

		if (src instanceof ArchiveItem) {
			eng.stop();
			state = STATE_STOPPED;
			listener.onEngineEnded(this);
		} else if (src instanceof StreamItem) {
			if (!positionChanged) {
				long lg = lag;
				setSource(src, false).main().onSuccess(md -> {
					if (source != src) return;
					startTimer();
					lag = lg;
					position = 0;
					state = STATE_PLAYING;
					VideoInfoView vi = (videoView != null) ? videoView.getVideoInfoView() : null;
					if (vi != null) vi.onPlayableChanged(src, src);
				});
			} else {
				((StreamItem) src).getEpg(startTime + position() + 1000).onSuccess(e -> {
					if ((e == null) || (source != src) || !isPlaying()) return;
					startTimer();
					startTime = e.getStartTime();
					endTime = e.getEndTime();
					position = 0;
					startStamp = System.currentTimeMillis();
					VideoInfoView vi = (videoView != null) ? videoView.getVideoInfoView() : null;
					if (vi != null) {
						src.getMediaDescription().main().and(e.getMediaDescription().main(), (sd, ed) -> {
							if ((source != src) || !isPlaying()) return;
							MediaDescriptionCompat.Builder dsc = new MediaDescriptionCompat.Builder();
							CharSequence sub = ed.getTitle();
							Uri icon = ed.getIconUri();
							dsc.setTitle(sd.getTitle());
							dsc.setDescription(ed.getSubtitle());
							dsc.setIconUri((icon != null) ? icon : sd.getIconUri());

							if (sub != null) {
								try (SharedTextBuilder b = SharedTextBuilder.get()) {
									b.append(sub).append(". ");
									TextUtils.dateToTimeString(b, startTime, false);
									b.append(" - ");
									TextUtils.dateToTimeString(b, endTime, false);
									dsc.setSubtitle(b.toString());
								}
							}

							vi.setDescription(src, dsc.build());
						});
					}
				});
			}
		}
	}

	private void reset() {
		stopTimer();
		source = null;
		state = STATE_STOPPED;
		position = -1;
		duration = completed(0L);
		positionChanged = false;
		startTime = endTime = lag = startStamp = bufferingStamp = 0L;
	}

	private void stopTimer() {
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	private void startTimer() {
		stopTimer();
		long dur = endTime - startTime;
		if (dur <= 0) return;
		long delay = dur - position();
		PlayableItem src = source;
		timer = App.get().getHandler().schedule(() -> {
			if ((source != src) || !isPlaying()) return;
			if (dur > position()) startTimer();
			else updateSource();
		}, delay);
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		long pos = position();
		//noinspection ConstantConditions
		if (isPlaying() && ((endTime - startTime) > 0) && (pos >= duration.peek())) {
			updateSource();
			pos = position();
		}
		return completed(pos);
	}

	private long position() {
		long pos = (startStamp == 0) ? position : position + currentTimeMillis() - startStamp - lag;
		return Math.max(pos, 0L);
	}

	@Override
	public void setPosition(long position) {
		if (!canSeek() || (position > duration.peek(0L))) return;

		stopTimer();
		boolean playing = isPlaying();

		if (source instanceof StreamItem) {
			long max = currentTimeMillis() - startTime;

			if (position >= max) {
				if (playing) {
					positionChanged = false;
					if (eng.getSource().getLocation().equals(source.getLocation())) return;
				}
				this.position = max;
			} else {
				this.position = position;
			}
		} else {
			this.position = Math.min(position, endTime - startTime);
		}

		lag = 0;
		startStamp = 0;
		positionChanged = true;
		if (!playing) return;
		eng.stop();
		PlayableItem i = createItem();
		if (i != null) eng.prepare(i);
	}

	private boolean isPlaying() {
		return state == STATE_PLAYING;
	}

	@Override
	public void start() {
		PlayableItem i = createItem();
		if (i == null) return;
		state = STATE_PLAYING;
		eng.prepare(i);
	}

	@Nullable
	private PlayableItem createItem() {
		PlayableItem src = source;
		Uri u = null;

		if (src instanceof StreamItem) {
			StreamItem s = (StreamItem) source;
			if (position == -1) {
				u = s.getLocation();
				position = System.currentTimeMillis() - startTime;
			} else {
				u = s.getLocation(startTime + position, Long.MAX_VALUE);
			}
		} else if (src instanceof ArchiveItem) {
			ArchiveItem a = (ArchiveItem) source;
			if (position == -1) position = 0;
			long start = a.getStartTime() + position;
			u = a.getParent().getLocation(start, a.getEndTime() - start);
		}

		if (u == null) {
			eng.stop();
			onEngineError(this, new IllegalArgumentException("Failed to play " + source.getName()));
			return null;
		} else {
			return new Stream(src, u);
		}
	}

	@Override
	public void stop() {
		eng.stop();
		reset();
	}

	@Override
	public void pause() {
		if (!canPause()) return;
		stopTimer();
		position = position();
		lag = 0;
		startStamp = 0;
		state = STATE_PAUSED;
		positionChanged = true;
		eng.stop();
	}

	@Override
	public void close() {
		eng.close();
		reset();
	}

	@Override
	public boolean canSeek() {
		return (startTime < endTime) && MediaEngine.super.canSeek();
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@NonNull
	@Override
	public FutureSupplier<Long> getDuration() {
		return duration;
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return SPEED;
	}

	@Override
	public void setSpeed(float speed) {
	}

	@Override
	public int getId() {
		return eng.getId();
	}

	@Override
	public void setVideoView(VideoView view) {
		eng.setVideoView(videoView = view);
	}

	@Override
	public float getVideoWidth() {
		return eng.getVideoWidth();
	}

	@Override
	public float getVideoHeight() {
		return eng.getVideoHeight();
	}

	@Override
	@Nullable
	public AudioEffects getAudioEffects() {
		return eng.getAudioEffects();
	}

	public boolean isSubtitlesSupported() {
		return eng.isSubtitlesSupported();
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		return eng.getAudioStreamInfo();
	}

	@Override
	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		return eng.getSubtitleStreamInfo();
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		return eng.getCurrentAudioStreamInfo();
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo i) {
		eng.setCurrentAudioStream(i);
	}

	@Nullable
	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		return eng.getCurrentSubtitleStreamInfo();
	}

	@Override
	public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {
		eng.setCurrentSubtitleStream(i);
	}

	@Override
	public boolean isAudioDelaySupported() {
		return eng.isAudioDelaySupported();
	}

	@Override
	public int getAudioDelay() {
		return eng.getAudioDelay();
	}

	@Override
	public void setAudioDelay(int milliseconds) {
		eng.setAudioDelay(milliseconds);
	}

	@Override
	public int getSubtitleDelay() {
		return eng.getSubtitleDelay();
	}

	@Override
	public void setSubtitleDelay(int milliseconds) {
		eng.setSubtitleDelay(milliseconds);
	}

	@Override
	public boolean setSurfaceSize(VideoView view) {
		return eng.setSurfaceSize(view);
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager,
																	 @Nullable AudioFocusRequestCompat audioFocusReq) {
		return eng.requestAudioFocus(audioManager, audioFocusReq);
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager,
																@Nullable AudioFocusRequestCompat audioFocusReq) {
		eng.releaseAudioFocus(audioManager, audioFocusReq);
	}

	@Override
	public boolean hasVideoMenu() {
		return eng.hasVideoMenu();
	}

	@Override
	public void contributeToMenu(OverlayMenu.Builder b) {
		eng.contributeToMenu(b);
	}

	@Override
	public void onEnginePrepared(MediaEngine engine) {
		engine.start();
	}

	@Override
	public void onEngineStarted(MediaEngine engine) {
		startStamp = currentTimeMillis();
		startTimer();
		listener.onEngineStarted(this);
	}

	@Override
	public void onEngineEnded(MediaEngine engine) {
		if (!isPlaying()) return;
		state = STATE_STOPPED;
		listener.onEngineEnded(this);
	}

	@Override
	public void onEngineBuffering(MediaEngine engine, int percent) {
		if (bufferingStamp == 0L) bufferingStamp = currentTimeMillis();
		listener.onEngineBuffering(this, percent);
	}

	@Override
	public void onEngineBufferingCompleted(MediaEngine engine) {
		if (bufferingStamp > 0) {
			assert bufferingStamp <= currentTimeMillis();
			lag += (currentTimeMillis() - bufferingStamp);
			bufferingStamp = 0L;
		}
		listener.onEngineBufferingCompleted(this);
	}

	@Override
	public void onVideoSizeChanged(MediaEngine engine, int width, int height) {
		listener.onVideoSizeChanged(this, width, height);
	}

	@Override
	public void onEngineError(MediaEngine engine, Throwable ex) {
		state = STATE_ERROR;
		listener.onEngineError(this, ex);
	}

	@NonNull
	@Override
	public String toString() {
		return super.toString();
	}

	private static final class Stream extends PlayableItemWrapper {
		private final Uri location;

		public Stream(PlayableItem item, Uri location) {
			super(item);
			this.location = location;
		}

		@NonNull
		@Override
		public Uri getLocation() {
			return location;
		}
	}
}
