package me.aap.fermata.media.engine;

import android.content.Context;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngineProvider {

	void init(Context ctx);

	String getId();

	String getEngineName();

	boolean canPlay(PlayableItem i);

	MediaEngine createEngine(Listener listener);
}
