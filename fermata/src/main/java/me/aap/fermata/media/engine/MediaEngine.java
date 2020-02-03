package me.aap.fermata.media.engine;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngine extends Closeable {

	int getId();

	void prepare(PlayableItem source);

	void start();

	void stop();

	void pause();

	PlayableItem getSource();

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

	long getDuration();

	long getPosition();

	void setPosition(long position);

	float getSpeed();

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

	interface Listener {

		default void onEnginePrepared(MediaEngine engine) {
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
