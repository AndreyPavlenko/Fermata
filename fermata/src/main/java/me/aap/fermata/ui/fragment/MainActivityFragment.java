package me.aap.fermata.ui.fragment;

import androidx.annotation.CallSuper;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public abstract class MainActivityFragment extends ActivityFragment {

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return (MainActivityDelegate) super.getActivityDelegate();
	}

	@Override
	public NavBarMediator getNavBarMediator() {
		return getActivityDelegate().getNavBarMediator();
	}

	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FloatingButton.Mediator.BackMenu.instance;
	}

	@CallSuper
	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) discardSelection();
	}

	@Override
	public boolean onBackPressed() {
		discardSelection();
		return super.onBackPressed();
	}

	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
	}

	public void discardSelection() {
	}
}
