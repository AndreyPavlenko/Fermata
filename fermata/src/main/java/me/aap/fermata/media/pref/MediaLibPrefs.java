package me.aap.fermata.media.pref;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLibPrefs extends BrowsableItemPrefs {
	Pref<BooleanSupplier> EXO_ENABLED = Pref.b("EXO_ENABLED", false).withInheritance(false);
	Pref<BooleanSupplier> VLC_ENABLED = Pref.b("VLC_ENABLED", false).withInheritance(false);
	Pref<BooleanSupplier> RESUME_PREVIOUS_APP =
			Pref.b("RESUME_PREVIOUS_APP", false).withInheritance(false);
	Pref<BooleanSupplier> RESUME_PREVIOUS_APP_ON_LEAVE =
			Pref.b("RESUME_PREVIOUS_APP_ON_LEAVE", false).withInheritance(false);
	Pref<Supplier<String>> PREVIOUS_MEDIA_APP =
			Pref.s("PREVIOUS_MEDIA_APP").withInheritance(false);

	default boolean getExoEnabledPref() {
		return getBooleanPref(EXO_ENABLED);
	}

	default boolean getVlcEnabledPref() {
		return getBooleanPref(VLC_ENABLED);
	}
}