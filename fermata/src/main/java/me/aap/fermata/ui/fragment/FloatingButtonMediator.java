package me.aap.fermata.ui.fragment;

import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.activity.ActivityDelegate;
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
		return isAddFolderEnabled(a.getActiveFragment()) ? R.drawable.add_folder :
				FloatingButton.Mediator.BackMenu.super.getIcon(fb);
	}

	@Override
	public void onClick(View v) {
		ActivityFragment f = ActivityDelegate.get(v.getContext()).getActiveFragment();

		if (isAddFolderEnabled(f)) {
			((FoldersFragment) f).addFolder();
		} else {
			FloatingButton.Mediator.BackMenu.super.onClick(v);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		ActivityFragment f = ActivityDelegate.get(v.getContext()).getActiveFragment();

		if (isAddFolderEnabled(f)) {
			FoldersFragment ff = (FoldersFragment) f;
			ff.addFolderPicker(ff.getMainActivity());
			return true;
		} else {
			return FloatingButton.Mediator.BackMenu.super.onLongClick(v);
		}
	}

	private boolean isAddFolderEnabled(ActivityFragment f) {
		return ((f instanceof FoldersFragment) && f.isRootPage());
	}
}
