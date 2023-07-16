package me.aap.fermata.media.engine;

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.text.TextUtils.isBlank;

import android.media.AudioManager;

import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngine extends Closeable {
	FutureSupplier<SubGrid> NO_SUBTITLES = completed(SubGrid.EMPTY);

	int getId();

	void prepare(PlayableItem source);

	void start();

	void stop();

	void pause();

	default boolean canPause() {
		return canSeek();
	}

	default boolean canSeek() {
		PlayableItem src = getSource();
		return (src != null) && src.isSeekable();
	}

	PlayableItem getSource();

	FutureSupplier<Long> getDuration();

	FutureSupplier<Long> getPosition();

	void setPosition(long position);

	FutureSupplier<Float> getSpeed();

	void setSpeed(float speed);

	void setVideoView(VideoView view);

	float getVideoWidth();

	float getVideoHeight();

	@Override
	void close();

	@Nullable
	default AudioEffects getAudioEffects() {
		return null;
	}

	default List<AudioStreamInfo> getAudioStreamInfo() {
		return Collections.emptyList();
	}

	@Nullable
	default AudioStreamInfo getCurrentAudioStreamInfo() {
		return null;
	}

	default void setCurrentAudioStream(@Nullable AudioStreamInfo i) {}

	default boolean isAudioDelaySupported() {
		return false;
	}

	default int getAudioDelay() {
		return 0;
	}

	default void setAudioDelay(int milliseconds) {}

	default boolean isSubtitlesSupported() {
		return false;
	}

	default FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		return completedEmptyList();
	}

	@Nullable
	default SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		return null;
	}

	default void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {}

	default FutureSupplier<Void> selectSubtitleStream() {
		var src = getSource();
		if (src == null) return completedVoid();
		var prefs = src.getPrefs();

		if (prefs.getSubEnabledPref()) {
			int delay = prefs.getSubDelayPref();
			if (delay != 0) setSubtitleDelay(delay);
			return selectMediaStream(prefs::getSubIdPref, prefs::getSubLangPref, prefs::getSubKeyPref,
					this::getSubtitleStreamInfo, si -> {
						if (getSource() == src) setCurrentSubtitleStream(si);
					});
		}

		return completedVoid();
	}

	default FutureSupplier<SubGrid> getCurrentSubtitles() {
		return NO_SUBTITLES;
	}


	default void addSubtitleConsumer(BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {}

	default void removeSubtitleConsumer(BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {}

	default int getSubtitleDelay() {
		return 0;
	}

	default void setSubtitleDelay(int milliseconds) {}

	static <I extends MediaStreamInfo> FutureSupplier<Void> selectMediaStream(
			Supplier<Long> idSupplier, Supplier<String> langSupplier, Supplier<String> keySupplier,
			Supplier<FutureSupplier<List<I>>> streamSupplier, Consumer<I> streamConsumer) {
		Long id = idSupplier.get();

		if ((id == null || id == -1) && isBlank(langSupplier.get()) && isBlank(keySupplier.get())) {
			return completedVoid();
		}

		return streamSupplier.get().main().map(streams -> {
			if (streams.isEmpty()) return null;

			if (id != null && id != -1) {
				for (I i : streams) {
					if (id == i.getId()) {
						streamConsumer.accept(i);
						return null;
					}
				}
			}

			String lang = langSupplier.get().trim();
			boolean hasMatching = false;

			if (!lang.isEmpty()) {
				List<I> filtered = null;

				for (var st = new StringTokenizer(lang, ","); st.hasMoreTokens(); ) {
					String l = st.nextToken().trim();

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
				for (var st = new StringTokenizer(key, ","); st.hasMoreTokens(); ) {
					String k = st.nextToken().trim();

					if (!k.isEmpty()) {
						k = k.toLowerCase();

						for (I i : streams) {
							String dsc = i.getDescription();
							if ((dsc != null) && TextUtils.containsWord(dsc.toLowerCase(), k)) {
								streamConsumer.accept(i);
								return null;
							}
						}
					}
				}
			}

			if (hasMatching) streamConsumer.accept(streams.get(0));
			return null;
		});
	}

	default boolean isVideoModeRequired() {
		PlayableItem src = getSource();
		return (src != null) && src.isVideo();
	}

	default boolean isSplitModeSupported() {
		return true;
	}

	default boolean setSurfaceSize(VideoView view) {
		return false;
	}

	default boolean requestAudioFocus(@Nullable AudioManager audioManager,
																		@Nullable AudioFocusRequestCompat audioFocusReq) {
		return (audioManager == null) || (audioFocusReq == null) ||
				(AudioManagerCompat.requestAudioFocus(audioManager, audioFocusReq) ==
						AUDIOFOCUS_REQUEST_GRANTED);
	}

	default void releaseAudioFocus(@Nullable AudioManager audioManager,
																 @Nullable AudioFocusRequestCompat audioFocusReq) {
		if ((audioManager != null) && (audioFocusReq != null))
			AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusReq);
	}

	default boolean hasVideoMenu() {
		return false;
	}

	default void contributeToMenu(OverlayMenu.Builder b) {}

	default boolean adjustVolume(int direction) {
		return false;
	}

	interface Listener {
		Listener DUMMY = new Listener() {};

		default void onEnginePrepared(MediaEngine engine) {}

		default void onEngineStarted(MediaEngine engine) {}

		default void onEngineEnded(MediaEngine engine) {}

		default void onEngineBuffering(MediaEngine engine, int percent) {}

		default void onEngineBufferingCompleted(MediaEngine engine) {}

		default void onEngineError(MediaEngine engine, Throwable ex) {}

		default void onVideoSizeChanged(MediaEngine engine, int width, int height) {}

		default void onSubtitleStreamChanged(MediaEngine engine, @Nullable SubtitleStreamInfo info) {}
	}
}
