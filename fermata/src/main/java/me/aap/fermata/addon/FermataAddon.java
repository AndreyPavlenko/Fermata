package me.aap.fermata.addon;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import me.aap.fermata.BuildConfig;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public interface FermataAddon {

	@IdRes
	int getAddonId();

	@NonNull
	AddonInfo getInfo();

	@NonNull
	ActivityFragment createFragment();

	default int getFragmentId() {
		return getAddonId();
	}

	default void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
	}

	default void install() {
	}

	default void uninstall() {
	}

	@NonNull
	static AddonInfo findAddonInfo(String name) {
		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (ai.className.equals(name)) return ai;
		}
		throw new RuntimeException("Addon not found: " + name);
	}
}
