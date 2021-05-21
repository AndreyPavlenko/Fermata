package me.aap.fermata.addon.tv;

import android.view.View;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
class FloatingButtonMediator implements FloatingButton.Mediator.BackMenu {
	static final FloatingButtonMediator instance = new FloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		return isAddSourceEnabled(a.getActiveFragment()) ? R.drawable.tv_add :
				BackMenu.super.getIcon(fb);
	}

	@Override
	public void onClick(View v) {
		ActivityFragment f = ActivityDelegate.get(v.getContext()).getActiveFragment();
		if (isAddSourceEnabled(f)) ((TvFragment) f).addSource();
		else BackMenu.super.onClick(v);
	}

	private boolean isAddSourceEnabled(ActivityFragment f) {
		return ((f instanceof TvFragment) && f.isRootPage());
	}
}
