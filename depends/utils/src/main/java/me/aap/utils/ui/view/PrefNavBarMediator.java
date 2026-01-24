package me.aap.utils.ui.view;

import java.util.List;

import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public abstract class PrefNavBarMediator extends CustomizableNavBarMediator
		implements PreferenceStore.Listener {

	protected abstract PreferenceStore getPreferenceStore(NavBarView nb);

	protected abstract Pref<?> getPref(NavBarView nb);

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		super.enable(nb, f);
		getPreferenceStore(nb).addBroadcastListener(this);
	}

	@Override
	public void disable(NavBarView nb) {
		super.disable(nb);
		getPreferenceStore(nb).removeBroadcastListener(this);
	}

	protected void reload(NavBarView nb) {
		super.disable(nb);
		super.enable(nb, nb.getActivity().getActiveFragment());
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		NavBarView nb = navBar;
		if ((nb != null) && prefs.contains(getPref(nb))) reload(nb);
	}
}
