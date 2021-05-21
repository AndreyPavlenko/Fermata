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
import me.aap.fermata.ui.activity.MainActivityDelegate;
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
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new FavoritesAdapter(getMainActivity(), b.getLib().getFavorites());
	}

	@Override
	public int getFragmentId() {
		return R.id.favorites_fragment;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.favorites);
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);
		FavoritesAdapter a = getAdapter();

		if (a.getListView().isSelectionActive() && a.hasSelectable() && a.hasSelected()) {
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(R.id.favorites_remove, R.drawable.favorite_filled, R.string.favorites_remove);
			getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
		}
	}

	protected boolean navBarMenuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.favorites_remove) {
			requireNonNull(getLib()).getFavorites().removeItems(filterMap(getAdapter().getList(),
					MediaItemWrapper::isSelected, (i, w, l) -> l.add((PlayableItem) w.getItem()),
					ArrayList::new));
			discardSelection();
			getAdapter().setParent(getAdapter().getParent());
			return true;
		}
		return super.navBarMenuItemSelected(item);
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

		FavoritesAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
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
	}
}
