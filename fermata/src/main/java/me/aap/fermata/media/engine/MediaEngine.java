package me.aap.fermata.media.engine;

import android.media.AudioManager;

import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.menu.OverlayMenu;

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngine extends Closeable {

	int getId();

	void prepare(PlayableItem source);

	void start();

	void stop();

	void pause();

	default boolean canPause() {
		PlayableItem src = getSource();
		return (src != null) && !src.isStream();
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

	default List<SubtitleStreamInfo> getSubtitleStreamInfo() {
		return Collections.emptyList();
	}

	default AudioStreamInfo getCurrentAudioStreamInfo() {
		return null;
	}

	default void setCurrentAudioStream(AudioStreamInfo i) {
	}

	default SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		return null;
	}

	default void setCurrentSubtitleStream(SubtitleStreamInfo i) {
	}

	default boolean isAudioDelaySupported() {
		return false;
	}

	default int getAudioDelay() {
		return 0;
	}

	default void setAudioDelay(int milliseconds) {
	}

	default boolean isSubtitleDelaySupported() {
		return false;
	}

	default int getSubtitleDelay() {
		return 0;
	}

	default void setSubtitleDelay(int milliseconds) {
	}

	default boolean setSurfaceSize(VideoView view) {
		return false;
	}

	default boolean requestAudioFocus(@Nullable AudioManager audioManager, @Nullable AudioFocusRequestCompat audioFocusReq) {
		return (audioManager == null) ||
				(AudioManagerCompat.requestAudioFocus(audioManager, audioFocusReq) == AUDIOFOCUS_REQUEST_GRANTED);
	}

	default void releaseAudioFocus(@Nullable AudioManager audioManager, @Nullable AudioFocusRequestCompat audioFocusReq) {
		if (audioManager != null)
			AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusReq);
	}

	default void contributeToMenu(OverlayMenu.Builder b) {
	}

	interface Listener {

		default void onEnginePrepared(MediaEngine engine) {
		}

		default void onEngineStarted(MediaEngine engine) {
		}

		default void onEngineEnded(MediaEngine engine) {
		}

		default void onEngineBuffering(MediaEngine engine, int percent) {
		}

		default void onEngineBufferingCompleted(MediaEngine engine) {
		}

		default void onVideoSizeChanged(MediaEngine engine, int width, int height) {
		}

		default void onEngineError(MediaEngine engine, Throwable ex) {
		}
	}
}
