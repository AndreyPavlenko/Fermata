package me.aap.fermata.ui.activity;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;

import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.event.EventBroadcaster;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityPrefs extends SharedPreferenceStore, EventBroadcaster<PreferenceStore.Listener> {
	int THEME_DARK = 0;
	int THEME_LIGHT = 1;
	int THEME_DAY_NIGHT = 2;
	int THEME_BLACK = 3;
	Pref<IntSupplier> THEME = Pref.i("THEME", THEME_DARK);
	Pref<BooleanSupplier> HIDE_BARS = Pref.b("HIDE_BARS", false);
	Pref<BooleanSupplier> FULLSCREEN = Pref.b("FULLSCREEN", false);

	default int getThemePref() {
		return getIntPref(THEME);
	}

	default void setThemePref(int value) {
		applyIntPref(THEME, value);
	}

	default boolean getHideBarsPref() {
		return getBooleanPref(HIDE_BARS);
	}

	default void setHideBarsPref(boolean value) {
		applyBooleanPref(HIDE_BARS, value);
	}

	default boolean getFullscreenPref() {
		return getBooleanPref(FULLSCREEN);
	}

	default void setFullscreenPref(boolean value) {
		applyBooleanPref(FULLSCREEN, value);
	}
}
