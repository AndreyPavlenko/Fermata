package me.aap.fermata.ui.activity;

import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityPrefs extends SharedPreferenceStore, EventBroadcaster<PreferenceStore.Listener> {
	int THEME_DARK = 0;
	int THEME_LIGHT = 1;
	int THEME_DAY_NIGHT = 2;
	int THEME_BLACK = 3;
	Pref<IntSupplier> THEME = Pref.i("THEME", THEME_DARK);
	Pref<IntSupplier> NAV_BAR_POS = Pref.i("NAV_BAR_POS", NavBarView.POSITION_BOTTOM);
	Pref<IntSupplier> NAV_BAR_POS_AA = Pref.i("NAV_BAR_POS_AA", NavBarView.POSITION_BOTTOM);
	Pref<BooleanSupplier> HIDE_BARS = Pref.b("HIDE_BARS", false);
	Pref<BooleanSupplier> FULLSCREEN = Pref.b("FULLSCREEN", false);
	Pref<BooleanSupplier> GRID_VIEW = Pref.b("GRID_VIEW", false);
	Pref<DoubleSupplier> MEDIA_ITEM_SCALE = Pref.f("MEDIA_ITEM_SCALE", 1);

	default int getThemePref() {
		return getIntPref(THEME);
	}

	default int getNavBarPosPref() {
		return getIntPref(NAV_BAR_POS);
	}

	default int getNavBarPosAAPref() {
		return getIntPref(NAV_BAR_POS_AA);
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

	default boolean getGridViewPref() {
		return getBooleanPref(GRID_VIEW);
	}

	default void setGridViewPref(boolean value) {
		applyBooleanPref(GRID_VIEW, value);
	}

	default float getMediaItemScalePref() {
		return getFloatPref(MEDIA_ITEM_SCALE);
	}
}
