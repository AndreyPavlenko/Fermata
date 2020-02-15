package me.aap.fermata.media.pref;

import me.aap.utils.function.BooleanSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLibPrefs extends BrowsableItemPrefs {
	Pref<BooleanSupplier> EXO_ENABLED = Pref.b("EXO_ENABLED", false).withInheritance(false);
	Pref<BooleanSupplier> VLC_ENABLED = Pref.b("VLC_ENABLED", false).withInheritance(false);

	default boolean getExoEnabledPref() {
		return getBooleanPref(EXO_ENABLED);
	}

	default boolean getVlcEnabledPref() {
		return getBooleanPref(VLC_ENABLED);
	}
}