package me.aap.fermata.media.engine;

import android.content.Context;

import me.aap.fermata.media.engine.MediaEngine.Listener;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngineProvider {

	void init(Context ctx);

	MediaEngine createEngine(Listener listener);
}
