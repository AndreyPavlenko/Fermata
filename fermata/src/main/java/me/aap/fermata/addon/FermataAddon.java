package me.aap.fermata.addon;

import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public interface FermataAddon {

	@IdRes
	int getAddonId();

	@NonNull
	AddonInfo getInfo();

	default void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
	}

	default void install() {
	}

	default void uninstall() {
	}

	default boolean handleIntent(MainActivityDelegate a, Intent intent) {
		return false;
	}

	@NonNull
	static AddonInfo findAddonInfo(String name) {
		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (ai.className.equals(name)) return ai;
		}
		throw new RuntimeException("Addon not found: " + name);
	}
}
