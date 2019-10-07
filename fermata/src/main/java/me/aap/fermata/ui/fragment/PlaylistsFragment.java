package me.aap.fermata.ui.fragment;

import java.util.List;
import java.util.stream.Collectors;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.MediaLib.Playlists;
import me.aap.fermata.media.pref.PlaylistPrefs;
import me.aap.fermata.media.pref.PlaylistsPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;
import me.aap.fermata.ui.view.MediaItemWrapper;

/**
 * @author Andrey Pavlenko
 */
public class PlaylistsFragment extends MediaLibFragment {

	@Override
	ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new PlaylistsAdapter(b.getLib().getPlaylists());
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
	public void initNavBarMenu(AppMenu menu) {
		PlaylistsAdapter a = getAdapter();
		if (!a.hasSelectable()) return;

		if (a.getListView().isSelectionActive()) {
			boolean hasSelected = a.hasSelected();

			menu.findItem(R.id.nav_select_all).setVisible(true);
			menu.findItem(R.id.nav_unselect_all).setVisible(true);

			if (hasSelected) {
				menu.findItem(R.id.nav_favorites_add).setVisible(true);
				menu.findItem(R.id.nav_playlist_remove).setVisible(true);
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
			case R.id.nav_favorites_add:
				getLib().getFavorites().addItems(getAdapter().getList().stream()
						.filter(MediaItemWrapper::isSelected).map(w -> (MediaLib.PlayableItem) w.getItem())
						.collect(Collectors.toList()));
				discardSelection();
				MediaLibFragment f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				return true;
			case R.id.nav_playlist_remove:
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

		PlaylistsAdapter(BrowsableItem parent) {
			super(parent);
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
