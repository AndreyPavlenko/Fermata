package me.aap.fermata.addon;

import androidx.annotation.NonNull;

import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public interface FermataFragmentAddon extends FermataAddon {

	@NonNull
	ActivityFragment createFragment();

	default int getFragmentId() {
		return getAddonId();
	}
}
