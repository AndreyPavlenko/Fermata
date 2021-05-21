package me.aap.fermata.addon;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

import static me.aap.utils.ui.UiUtils.ID_NULL;

/**
 * @author Andrey Pavlenko
 */
public interface FermataAddon {

	@IdRes
	int getAddonId();

	@NonNull
	ActivityFragment createFragment();

	default void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
	}
}
