package me.aap.fermata.ui.fragment;

import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public class NavBarMediator implements NavBarView.Mediator, OverlayMenu.SelectionHandler {
	public static final NavBarMediator instance = new NavBarMediator();

	private NavBarMediator() {
	}

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		addButton(nb, R.drawable.folder, R.string.folders, R.id.nav_folders);
		addButton(nb, R.drawable.favorite_filled, R.string.favorites, R.id.nav_favorites);
		addButton(nb, R.drawable.playlist, R.string.playlists, R.id.nav_playlist);
		addButton(nb, R.drawable.menu, R.string.settings, R.id.nav_settings, this::showMenu);
	}

	public void showMenu(View v) {
		showMenu(MainActivityDelegate.get(v.getContext()));
	}

	@Override
	public void showMenu(NavBarView nb) {
		showMenu((View) nb);
	}

	public void showMenu(MainActivityDelegate a) {
		OverlayMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.inflate(R.layout.nav_menu);
		menu.findItem(R.id.nav_got_to_current).setVisible(a.hasCurrent());
		if (a.isCarActivity()) menu.findItem(R.id.nav_exit).setVisible(false);

		ActivityFragment f = a.getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).initNavBarMenu(menu);

		menu.show(this);
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_got_to_current:
				MainActivityDelegate.get(item.getContext()).goToCurrent();
				return true;
			case R.id.nav_settings:
				itemSelected(R.id.nav_settings, MainActivityDelegate.get(item.getContext()));
				MainActivityDelegate.get(item.getContext()).showFragment(R.id.nav_settings);
				return true;
			case R.id.nav_exit:
				MainActivityDelegate.get(item.getContext()).finish();
				return true;
		}

		ActivityFragment f = MainActivityDelegate.get(item.getContext()).getActiveFragment();
		return (f instanceof MainActivityFragment) && ((MainActivityFragment) f).navBarMenuItemSelected(item);
	}
}
