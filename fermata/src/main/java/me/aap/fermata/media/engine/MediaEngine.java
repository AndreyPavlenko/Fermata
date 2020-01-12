package me.aap.fermata.media.engine;

import android.view.SurfaceHolder;

import androidx.annotation.Nullable;

import java.io.Closeable;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngine extends Closeable {

	String getId();

	void prepare(PlayableItem source);

	void start();

	void stop();

	void pause();

	PlayableItem getSource();

	long getPosition();

	void setPosition(long position);

	float getSpeed();

	void setSpeed(float speed);

	void setSurface(SurfaceHolder surface);

	float getVideoWidth();

	float getVideoHeight();

	boolean canPlay(PlayableItem i);

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
