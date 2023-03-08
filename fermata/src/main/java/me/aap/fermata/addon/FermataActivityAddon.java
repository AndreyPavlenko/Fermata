package me.aap.fermata.addon;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public interface FermataActivityAddon extends FermataAddon {

	default void onActivityCreate(MainActivityDelegate a) {
	}

	default void onActivityDestroy(MainActivityDelegate a) {
	}

	default void onActivityResume(MainActivityDelegate a) {
	}

	default void onActivityPause(MainActivityDelegate a) {
	}
}
