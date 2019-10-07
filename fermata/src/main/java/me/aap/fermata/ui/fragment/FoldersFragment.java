package me.aap.fermata.ui.fragment;

import android.content.Intent;
import android.net.Uri;

import java.util.List;
import java.util.stream.Collectors;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FoldersPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;
import me.aap.fermata.ui.view.MediaItemWrapper;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

/**
 * @author Andrey Pavlenko
 */
public class FoldersFragment extends MediaLibFragment {

	@Override
	ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new FoldersAdapter(b.getLib().getFolders());
	}

	@Override
	public int getFragmentId() {
		return R.id.nav_folders;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.folders);
	}

	@Override
	public void initNavBarMenu(AppMenu menu) {
		if (isRootFolder()) {
			menu.findItem(R.id.nav_add_folder).setVisible(true);
		} else {
			FoldersAdapter a = getAdapter();
			if (!a.hasSelectable()) return;

			if (a.getListView().isSelectionActive()) {
				boolean hasSelected = a.hasSelected();

				menu.findItem(R.id.nav_select_all).setVisible(true);
				menu.findItem(R.id.nav_unselect_all).setVisible(true);

				if (hasSelected) {
					menu.findItem(R.id.nav_favorites_add).setVisible(true);
					getMainActivity().initPlaylistMenu(menu);
				}
			} else {
				menu.findItem(R.id.nav_select).setVisible(true);
			}
		}

		super.initNavBarMenu(menu);
	}

	public boolean navBarMenuItemSelected(AppMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_add_folder:
				addFolder();
				return true;
			case R.id.nav_select:
			case R.id.nav_select_all:
				getAdapter().getListView().select(true);
				return true;
			case R.id.nav_unselect_all:
				getAdapter().getListView().select(false);
				return true;
			case R.id.nav_favorites_add:
				getLib().getFavorites().addItems(getAdapter().getList().stream()
						.filter(MediaItemWrapper::isSelected).map(w -> (PlayableItem) w.getItem())
						.collect(Collectors.toList()));
				discardSelection();
				MediaLibFragment f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				return true;
			case R.id.playlist_add:
				AppMenu menu = item.getMenu();
				getMainActivity().createPlaylistMenu(menu);
				menu.show(this::navBarMenuItemSelected);
				return true;
			case R.id.playlist_create:
				getMainActivity().createPlaylist(getAdapter().getSelectedItems(),
						getAdapter().getParent().getName());
				return true;
			case R.id.playlist_add_item:
				getMainActivity().addToPlaylist(item.getTitle().toString(), getAdapter().getSelectedItems());
				return true;
		}

		return false;
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getLib().getFolders());
	}

	public void addFolder() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		getMainActivity().startActivityForResult(this::addFolder, intent);
	}

	private void addFolder(int result, Intent data) {
		if (data == null) return;

		Uri uri = data.getData();
		if (uri == null) return;

		FermataApplication.get().getContentResolver()
				.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
		Folders folders = getLib().getFolders();
		folders.addItem(uri);
		getAdapter().setParent(folders);
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getFolders().isFoldersItemId(i.getId());
	}

	private Folders getFolders() {
		return getLib().getFolders();
	}

	private boolean isRootFolder() {
		return (getAdapter().getParent() instanceof Folders);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		FoldersAdapter a = getAdapter();
		if (a.isCallbackCall()) return;
		if (prefs.contains(FoldersPrefs.FOLDERS) && isRootFolder()) a.reload();
		else super.onPreferenceChanged(store, prefs);
	}

	private final class FoldersAdapter extends ListAdapter {

		FoldersAdapter(BrowsableItem parent) {
			super(parent);
		}

		public boolean isLongPressDragEnabled() {
			return isRootFolder();
		}

		public boolean isItemViewSwipeEnabled() {
			return isRootFolder();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}
	}
}
