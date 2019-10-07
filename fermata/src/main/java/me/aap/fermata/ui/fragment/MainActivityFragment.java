package me.aap.fermata.ui.fragment;

import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityFragment {

	int getFragmentId();

	CharSequence getTitle();

	default boolean isRootPage() {
		return true;
	}

	default boolean onBackPressed() {
		return false;
	}

	default void discardSelection() {
	}

	default void initNavBarMenu(AppMenu menu) {
	}

	default void navBarItemReselected(int itemId) {
	}

	default boolean navBarMenuItemSelected(AppMenuItem item) {
		return false;
	}
}
