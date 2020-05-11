package me.aap.fermata.ui.fragment;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.MediaLib.Playlists;
import me.aap.fermata.media.pref.PlaylistPrefs;
import me.aap.fermata.media.pref.PlaylistsPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemWrapper;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.collection.CollectionUtils.filterMap;

/**
 * @author Andrey Pavlenko
 */
public class PlaylistsFragment extends MediaLibFragment {

	@Override
	ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new PlaylistsAdapter(getMainActivity(), b.getLib().getPlaylists());
	}

	@Override
	public int getFragmentId() {
		return R.id.nav_playlist;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.playlists);
	}

	@Override
	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getLib().getPlaylists());
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		PlaylistsAdapter a = getAdapter();
		if (!a.hasSelectable()) return;

		OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);

		if (a.getListView().isSelectionActive()) {
			b.addItem(R.id.nav_select_all, R.drawable.check_box, R.string.select_all);
			b.addItem(R.id.nav_unselect_all, R.drawable.check_box_blank, R.string.unselect_all);

			if (a.hasSelected()) {
				b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);
				b.addItem(R.id.playlist_remove, R.drawable.playlist_remove, R.string.playlist_remove);
			}
		} else {
			b.addItem(R.id.nav_select, R.drawable.check_box, R.string.select);
		}

		super.contributeToNavBarMenu(builder);
	}

	public boolean navBarMenuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_select:
			case R.id.nav_select_all:
				getAdapter().getListView().select(true);
				return true;
			case R.id.nav_unselect_all:
				getAdapter().getListView().select(false);
				return true;
			case R.id.favorites_add:
				requireNonNull(getLib()).getFavorites().addItems(filterMap(getAdapter().getList(),
						MediaItemWrapper::isSelected, (i, w, l) -> l.add((MediaLib.PlayableItem) w.getItem()),
						ArrayList::new));
				discardSelection();
				MediaLibFragment f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				return true;
			case R.id.playlist_remove:
				getMainActivity().removeFromPlaylist((Playlist) getAdapter().getParent(), getAdapter().getSelectedItems());
				return true;
		}

		return false;
	}

	@Override
	protected boolean isSupportedItem(MediaLib.Item i) {
		return getPlaylists().isPlaylistsItemId(i.getId());
	}

	private Playlists getPlaylists() {
		return getLib().getPlaylists();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		PlaylistsAdapter a = getAdapter();
		if (a.isCallbackCall() || (a.getParent() == null)) return;

		if (prefs.contains(PlaylistsPrefs.PLAYLIST_IDS) && (a.getParent() == getLib().getPlaylists())) {
			a.reload();
		} else if (prefs.contains(PlaylistPrefs.PLAYLIST_ITEMS)) {
			a.reload();
		} else {
			super.onPreferenceChanged(store, prefs);
		}
	}

	private class PlaylistsAdapter extends ListAdapter {

		PlaylistsAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem p = getParent();
			if (p instanceof Playlist) ((Playlist) p).removeItem(position);
			else ((Playlists) p).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem p = getParent();
			if (p instanceof Playlist) ((Playlist) p).moveItem(fromPosition, toPosition);
			else ((Playlists) p).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}
	}
}
