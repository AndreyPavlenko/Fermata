package me.aap.fermata.ui.activity;

import static me.aap.fermata.BuildConfig.AUTO;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityPrefs
		extends SharedPreferenceStore, EventBroadcaster<PreferenceStore.Listener> {
	int THEME_DARK = 0;
	int THEME_LIGHT = 1;
	int THEME_DAY_NIGHT = 2;
	int THEME_BLACK = 3;
	int THEME_STAR_WARS = 4;
	int CLOCK_POS_NONE = 0;
	int CLOCK_POS_LEFT = 1;
	int CLOCK_POS_RIGHT = 2;
	int CLOCK_POS_CENTER = 3;
	int LOCALE_EN = 0;
	int LOCALE_RU = 1;
	int LOCALE_IT = 2;
	int LOCALE_TR = 3;
	int LOCALE_DE = 4;
	int LOCALE_PT = 5;
	Pref<IntSupplier> THEME_MAIN = Pref.i("THEME_MAIN", THEME_DARK);
	Pref<BooleanSupplier> HIDE_BARS = Pref.b("HIDE_BARS", false);
	Pref<BooleanSupplier> FULLSCREEN = Pref.b("FULLSCREEN", false);
	Pref<BooleanSupplier> SHOW_PG_UP_DOWN = Pref.b("SHOW_PG_UP_DOWN", true);
	Pref<BooleanSupplier> USE_DPAD_CURSOR = AUTO ? Pref.b("USE_DPAD_CURSOR", true) : null;
	Pref<IntSupplier> NAV_BAR_POS = Pref.i("NAV_BAR_POS", NavBarView.POSITION_BOTTOM);
	Pref<DoubleSupplier> NAV_BAR_SIZE = Pref.f("NAV_BAR_SIZE", 1f);
	Pref<DoubleSupplier> TOOL_BAR_SIZE = Pref.f("TOOL_BAR_SIZE", 1f);
	Pref<DoubleSupplier> CONTROL_PANEL_SIZE = Pref.f("CONTROL_PANEL_SIZE", 1f);
	Pref<DoubleSupplier> TEXT_ICON_SIZE = Pref.f("TEXT_ICON_SIZE", 1f);
	Pref<BooleanSupplier> GRID_VIEW = Pref.b("GRID_VIEW", false);
	Pref<DoubleSupplier> P_SPLIT_PERCENT = Pref.f("P_SPLIT_PERCENT", 0.6f);
	Pref<DoubleSupplier> L_SPLIT_PERCENT = Pref.f("L_SPLIT_PERCENT", 0.4f);
	Pref<DoubleSupplier> P_SPLIT_PERCENT_SUB = Pref.f("P_SPLIT_PERCENT_SUB", 0.5f);
	Pref<DoubleSupplier> L_SPLIT_PERCENT_SUB = Pref.f("L_SPLIT_PERCENT_SUB", 0.5f);
	Pref<Supplier<String>> SHOW_ADDON_ON_START = Pref.s("SHOW_ADDON_ON_START", (String) null);
	Pref<BooleanSupplier> CHECK_UPDATES = Pref.b("CHECK_UPDATES", true);
	Pref<BooleanSupplier> SYS_BARS_ON_VIDEO_TOUCH = Pref.b("SYS_BARS_ON_VIDEO_TOUCH", false);
	Pref<BooleanSupplier> LANDSCAPE_VIDEO = Pref.b("LANDSCAPE_VIDEO", false);
	Pref<BooleanSupplier> CHANGE_BRIGHTNESS = Pref.b("CHANGE_BRIGHTNESS", false);
	Pref<IntSupplier> BRIGHTNESS = Pref.i("BRIGHTNESS", 255);
	Pref<BooleanSupplier> VOICE_CONTROl_ENABLED = Pref.b("VOICE_CONTROl_ENABLED", false);
	Pref<BooleanSupplier> VOICE_CONTROl_FB = Pref.b("VOICE_CONTROl_FB", false);
	Pref<Supplier<String>> VOICE_CONTROL_SUBST = Pref.s("VOICE_CONTROL_SUBST", "");
	Pref<Supplier<String>> VOICE_CONTROL_LANG =
			Pref.s("VOICE_CONTROL_LANG", () -> Locale.getDefault().toLanguageTag());
	Pref<IntSupplier> CLOCK_POS = Pref.i("CLOCK_POS", CLOCK_POS_NONE);
	Pref<IntSupplier> LOCALE = Pref.i("LOCALE", () -> switch (Locale.getDefault().getLanguage()) {
		case "ru" -> LOCALE_RU;
		case "it" -> LOCALE_IT;
		case "tr" -> LOCALE_TR;
		case "de" -> LOCALE_DE;
		case "pt" -> LOCALE_PT;
		default -> LOCALE_EN;
	});

	Pref<IntSupplier> THEME_AA = Pref.i("THEME_AA", THEME_DARK);
	Pref<BooleanSupplier> HIDE_BARS_AA = AUTO ? Pref.b("HIDE_BARS_AA", false) : null;
	Pref<BooleanSupplier> FULLSCREEN_AA = AUTO ? Pref.b("FULLSCREEN_AA", false) : null;
	Pref<BooleanSupplier> SHOW_PG_UP_DOWN_AA = AUTO ? Pref.b("SHOW_PG_UP_DOWN_AA", true) : null;
	Pref<IntSupplier> NAV_BAR_POS_AA =
			AUTO ? Pref.i("NAV_BAR_POS_AA", NavBarView.POSITION_BOTTOM) : null;
	Pref<DoubleSupplier> NAV_BAR_SIZE_AA = AUTO ? Pref.f("NAV_BAR_SIZE_AA", 1f) : null;
	Pref<DoubleSupplier> TOOL_BAR_SIZE_AA = AUTO ? Pref.f("TOOL_BAR_SIZE_AA", 1f) : null;
	Pref<DoubleSupplier> CONTROL_PANEL_SIZE_AA = AUTO ? Pref.f("CONTROL_PANEL_SIZE_AA", 1f) : null;
	Pref<DoubleSupplier> TEXT_ICON_SIZE_AA = AUTO ? Pref.f("TEXT_ICON_SIZE_AA", 1f) : null;
	Pref<BooleanSupplier> GRID_VIEW_AA = AUTO ? Pref.b("GRID_VIEW_AA", false) : null;

	static MainActivityPrefs get() {
		return MainActivityDelegate.Prefs.instance;
	}

	static boolean hasThemePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(THEME_AA);
		return prefs.contains(THEME_MAIN);
	}

	default int getThemePref(boolean auto) {
		return (AUTO && auto) ? getIntPref(THEME_AA) : getIntPref(THEME_MAIN);
	}

	@Nullable
	default String getShowAddonOnStartPref() {
		return getStringPref(SHOW_ADDON_ON_START);
	}

	default void setShowAddonOnStartPref(@Nullable String className) {
		applyStringPref(SHOW_ADDON_ON_START, className);
	}

	default boolean getCheckUpdatesPref() {
		return getBooleanPref(CHECK_UPDATES);
	}

	static boolean hasFullscreenPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(FULLSCREEN_AA);
		return prefs.contains(FULLSCREEN);
	}

	default boolean getFullscreenPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(FULLSCREEN_AA);
		return getBooleanPref(FULLSCREEN);
	}

	static boolean hasHideBarsPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(HIDE_BARS_AA);
		return prefs.contains(HIDE_BARS);
	}

	default boolean getHideBarsPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(HIDE_BARS_AA);
		return getBooleanPref(HIDE_BARS);
	}

	default boolean getShowPgUpDownPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(SHOW_PG_UP_DOWN_AA);
		return getBooleanPref(SHOW_PG_UP_DOWN);
	}

	default boolean useDpadCursor(MainActivityDelegate a) {
		return AUTO && a.isCarActivity() && getBooleanPref(USE_DPAD_CURSOR);
	}

	static boolean hasNavBarPosPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(NAV_BAR_POS_AA);
		return prefs.contains(NAV_BAR_POS);
	}

	default int getNavBarPosPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getIntPref(NAV_BAR_POS_AA);
		return getIntPref(NAV_BAR_POS);
	}

	static boolean hasNavBarSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(NAV_BAR_SIZE_AA);
		return prefs.contains(NAV_BAR_SIZE);
	}

	default float getNavBarSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getFloatPref(NAV_BAR_SIZE_AA);
		return getFloatPref(NAV_BAR_SIZE);
	}

	static boolean hasToolBarSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(TOOL_BAR_SIZE_AA);
		return prefs.contains(TOOL_BAR_SIZE);
	}

	default float getToolBarSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getFloatPref(TOOL_BAR_SIZE_AA);
		return getFloatPref(TOOL_BAR_SIZE);
	}

	static boolean hasControlPanelSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(CONTROL_PANEL_SIZE_AA);
		return prefs.contains(CONTROL_PANEL_SIZE);
	}

	default float getControlPanelSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getFloatPref(CONTROL_PANEL_SIZE_AA);
		return getFloatPref(CONTROL_PANEL_SIZE);
	}

	static boolean hasTextIconSizePref(MainActivityDelegate a, List<Pref<?>> prefs) {
		if (AUTO && a.isCarActivity()) return prefs.contains(TEXT_ICON_SIZE_AA);
		return prefs.contains(TEXT_ICON_SIZE);
	}

	default float getTextIconSizePref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getFloatPref(TEXT_ICON_SIZE_AA);
		return getFloatPref(TEXT_ICON_SIZE);
	}

	static boolean hasGridViewPref(MainActivityDelegate a, List<Pref<?>> prefs) {
		return prefs.contains(getGridViewPrefKey(a));
	}

	static Pref<BooleanSupplier> getGridViewPrefKey(MainActivityDelegate a) {
		return (AUTO && a.isCarActivity()) ? GRID_VIEW_AA : GRID_VIEW;
	}

	default boolean getGridViewPref(MainActivityDelegate a) {
		if (AUTO && a.isCarActivity()) return getBooleanPref(GRID_VIEW_AA);
		return getBooleanPref(GRID_VIEW);
	}

	default void setGridViewPref(MainActivityDelegate a, boolean value) {
		applyBooleanPref(getGridViewPrefKey(a), value);
	}

	default boolean getSysBarsOnVideoTouchPref() {
		return getBooleanPref(SYS_BARS_ON_VIDEO_TOUCH);
	}

	default boolean getLandscapeVideoPref() {
		return getBooleanPref(LANDSCAPE_VIDEO);
	}

	default boolean getChangeBrightnessPref() {
		return getBooleanPref(CHANGE_BRIGHTNESS);
	}

	default int getBrightnessPref() {
		return getIntPref(BRIGHTNESS);
	}

	default boolean getVoiceControlEnabledPref() {
		return getBooleanPref(VOICE_CONTROl_ENABLED);
	}

	default boolean getVoiceControlFBPref() {
		return getBooleanPref(VOICE_CONTROl_FB);
	}

	default String getVoiceControlLang(MainActivityDelegate a) {
		return a.getPrefs().getStringPref(VOICE_CONTROL_LANG);
	}

	default int getClockPosPref() {
		return getIntPref(CLOCK_POS);
	}

	default Locale getLocalePref() {
		return switch (getIntPref(LOCALE)) {
			case LOCALE_RU -> new Locale("ru");
			case LOCALE_IT -> Locale.ITALIAN;
			case LOCALE_TR -> new Locale("tr");
			case LOCALE_DE -> new Locale("de");
			case LOCALE_PT -> new Locale("pt");
			default -> Locale.ENGLISH;
		};
	}
}
