package me.aap.fermata.addon;

import me.aap.fermata.media.service.FermataMediaService;
import me.aap.fermata.media.service.MediaSessionCallback;

/**
 * @author Andrey Pavlenko
 */
public interface FermataMediaServiceAddon extends FermataAddon {

	default void onServiceCreate(MediaSessionCallback cb) {
	}

	default void onServiceDestroy(MediaSessionCallback cb) {
	}
}
