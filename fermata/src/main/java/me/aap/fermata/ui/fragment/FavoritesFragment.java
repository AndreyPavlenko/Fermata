package me.aap.fermata.ui.fragment;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FavoritesPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;
import me.aap.fermata.ui.view.MediaItemWrapper;

import static java.util.Objects.requireNonNull;
import static me.aap.fermata.util.Utils.filterMap;

/**
 * @author Andrey Pavlenko
 */
public class FavoritesFragment extends MediaLibFragment {

	@Override
	ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new FavoritesAdapter(b.getLib().getFavorites());
	}

	@Override
	public int getFragmentId() {
		return R.id.nav_favorites;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.favorites);
	}

	@Override
	public void initNavBarMenu(AppMenu menu) {
		FavoritesAdapter a = getAdapter();
		if (!a.hasSelectable()) return;

		if (a.getListView().isSelectionActive()) {
			boolean hasSelected = a.hasSelected();

			menu.findItem(R.id.nav_select_all).setVisible(true);
			menu.findItem(R.id.nav_unselect_all).setVisible(true);

			if (hasSelected) {
				menu.findItem(R.id.nav_favorites_remove).setVisible(true);
				getMainActivity().initPlaylistMenu(menu);
			}
		} else {
			menu.findItem(R.id.nav_select).setVisible(true);
		}

		super.initNavBarMenu(menu);
	}

	public boolean navBarMenuItemSelected(AppMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_select:
			case R.id.nav_select_all:
				getAdapter().getListView().select(true);
				return true;
			case R.id.nav_unselect_all:
				getAdapter().getListView().select(false);
				return true;
			case R.id.nav_favorites_remove:
				requireNonNull(getLib()).getFavorites().removeItems(filterMap(getAdapter().getList(),
						MediaItemWrapper::isSelected, (i, w, l) -> l.add((PlayableItem) w.getItem()),
						ArrayList::new));
				getAdapter().setParent(getAdapter().getParent());
				discardSelection();
				return true;
			case R.id.playlist_add:
				AppMenu menu = item.getMenu();
				getMainActivity().createPlaylistMenu(menu);
				menu.show(this::navBarMenuItemSelected);
				return true;
			case R.id.playlist_create:
				getMainActivity().createPlaylist(getAdapter().getSelectedItems(), "");
				return true;
			case R.id.playlist_add_item:
				getMainActivity().addToPlaylist(item.getTitle().toString(), getAdapter().getSelectedItems());
				return true;
		}

		return false;
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getFavorites().isFavoriteItemId(i.getId());
	}

	private Favorites getFavorites() {
		return getLib().getFavorites();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		FavoritesAdapter a = getAdapter();
		if (!a.isCallbackCall() && prefs.contains(FavoritesPrefs.FAVORITES)) a.reload();
		else super.onPreferenceChanged(store, prefs);
	}

	private class FavoritesAdapter extends ListAdapter {

		FavoritesAdapter(BrowsableItem parent) {
			super(parent);
		}

		@Override
		protected void onItemDismiss(int position) {
			getFavorites().removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			getFavorites().moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		@Override
		public void setParent(BrowsableItem parent) {
			assert (parent == getLib().getFavorites());
			super.setParent(parent);
		}
	}
}
