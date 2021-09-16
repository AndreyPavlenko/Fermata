package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.FOCUS_UP;
import static me.aap.utils.ui.UiUtils.isVisible;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.FloatingButton.Mediator.BackMenu;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class FloatingButtonMediator implements BackMenu {
	public static final FloatingButtonMediator instance = new FloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		if (a.isVideoMode() || !a.isRootPage()) return getBackIcon();
		if (isAddFolderEnabled(a.getActiveFragment())) return R.drawable.add_folder;
		return getMenuIcon();
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (a.isVideoMode() || !a.isRootPage()) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (isAddFolderEnabled(f)) ((FoldersFragment) f).addFolder();
			else showMenu((FloatingButton) v);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		ActivityFragment f = ActivityDelegate.get(v.getContext()).getActiveFragment();
		if (isAddFolderEnabled(f)) ((FoldersFragment) f).addFolderPicker();
		else showMenu((FloatingButton) v);
		return true;
	}

	@Nullable
	@Override
	public View focusSearch(FloatingButton fb, int direction) {
		if (direction == FOCUS_RIGHT) {
			NavBarView n = MainActivityDelegate.get(fb.getContext()).getNavBar();
			return (isVisible(n) && n.isRight()) ? n.focusSearch() : MediaItemListView.focusSearchLast(fb);
		} else if (direction == FOCUS_LEFT) {
			return MediaItemListView.focusSearchActive(fb);
		} else if (direction == FOCUS_UP) {
			ToolBarView tb = MainActivityDelegate.get(fb.getContext()).getToolBar();
			if (isVisible(tb)) return tb.focusSearch();
		}

		return null;
	}

	private boolean isAddFolderEnabled(ActivityFragment f) {
		return ((f instanceof FoldersFragment) && f.isRootPage());
	}
}
