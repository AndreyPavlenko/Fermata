package me.aap.fermata.ui.fragment;

import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public class FloatingButtonMediator implements FloatingButton.Mediator.BackMenu {
	public static final FloatingButtonMediator instance = new FloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		return isAddFolderEnabled(a) ? R.drawable.add_folder :
				FloatingButton.Mediator.BackMenu.super.getIcon(fb);
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (isAddFolderEnabled(a)) {
			((FoldersFragment) a.getActiveFragment()).addFolder();
		} else {
			FloatingButton.Mediator.BackMenu.super.onClick(v);
		}
	}

	private boolean isAddFolderEnabled(MainActivityDelegate a) {
		if (!a.getAppActivity().isCarActivity()) {
			ActivityFragment f = a.getActiveFragment();
			return ((f instanceof FoldersFragment) && f.isRootPage());
		} else {
			return false;
		}
	}
}
