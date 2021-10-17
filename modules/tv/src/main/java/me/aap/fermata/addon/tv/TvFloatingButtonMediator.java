package me.aap.fermata.addon.tv;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.FloatingButtonMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
class TvFloatingButtonMediator extends FloatingButtonMediator {
	static final TvFloatingButtonMediator instance = new TvFloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		return (a.isVideoMode() || !a.isRootPage()) ? getBackIcon() : R.drawable.tv_add;
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (a.isVideoMode() || !a.isRootPage()) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (f instanceof TvFragment) ((TvFragment) f).addSource();
		}
	}

}
