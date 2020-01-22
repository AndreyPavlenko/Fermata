package me.aap.fermata.media.pref;

import me.aap.fermata.function.BooleanSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLibPrefs extends BrowsableItemPrefs {
	Pref<BooleanSupplier> EXO_ENABLED = Pref.b("EXO_ENABLED", false).withInheritance(false);

	default boolean getExoEnabledPref() {
		return getBooleanPref(EXO_ENABLED);
	}
}