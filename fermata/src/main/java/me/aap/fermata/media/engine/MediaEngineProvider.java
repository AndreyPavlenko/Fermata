package me.aap.fermata.media.engine;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngineProvider {

	void init(Context ctx);

	MediaEngine createEngine(Listener listener);

	default boolean getMediaMetadata(MetadataBuilder meta, PlayableItem item) {
		return false;
	}
}
