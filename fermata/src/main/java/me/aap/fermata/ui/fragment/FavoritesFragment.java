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
import me.aap.fermata.ui.view.MediaItemWrapper;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.collection.CollectionUtils.filterMap;

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
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		FavoritesAdapter a = getAdapter();
		if (!a.hasSelectable()) return;

		OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);

		if (a.getListView().isSelectionActive()) {
			b.addItem(R.id.nav_select_all, R.drawable.check_box, R.string.select_all);
			b.addItem(R.id.nav_unselect_all, R.drawable.check_box_blank, R.string.unselect_all);

			if (a.hasSelected()) {
				b.addItem(R.id.favorites_remove, R.drawable.favorite_filled, R.string.favorites_remove);
				getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
			}
		} else {
			b.addItem(R.id.nav_select, R.drawable.check_box, R.string.select);
		}

		super.contributeToNavBarMenu(builder);
	}

	private boolean navBarMenuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_select:
			case R.id.nav_select_all:
				getAdapter().getListView().select(true);
				return true;
			case R.id.nav_unselect_all:
				getAdapter().getListView().select(false);
				return true;
			case R.id.favorites_remove:
				requireNonNull(getLib()).getFavorites().removeItems(filterMap(getAdapter().getList(),
						MediaItemWrapper::isSelected, (i, w, l) -> l.add((PlayableItem) w.getItem()),
						ArrayList::new));
				getAdapter().setParent(getAdapter().getParent());
				discardSelection();
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
