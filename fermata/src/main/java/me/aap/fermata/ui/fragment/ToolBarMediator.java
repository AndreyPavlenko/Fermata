package me.aap.fermata.ui.fragment;

import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class ToolBarMediator implements ToolBarView.Mediator {
	public static final ToolBarView.Mediator instance = BackTitleFilter.instance.join(new ToolBarMediator());

	private ToolBarMediator() {
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		addButton(tb, R.drawable.title, ToolBarMediator::onViewButtonClick, R.id.tool_bar_view);
		addButton(tb, R.drawable.sort, ToolBarMediator::onSortButtonClick, R.id.tool_bar_sort);
	}

	private static void onViewButtonClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		OverlayMenu m = a.getToolBarMenu();
		f.discardSelection();
		m.show(R.layout.view_menu, ToolBarMediator::onViewMenuItemClick);
	}

	private static boolean onViewMenuItemClick(OverlayMenuItem item) {
		MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return false;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
		f.discardSelection();
		OverlayMenu menu;

		switch (item.getItemId()) {
			case R.id.tool_view_title:
				menu = item.getMenu();
				menu.findItem(R.id.tool_seq_num).setChecked(prefs.getTitleSeqNumPref());
				menu.findItem(R.id.tool_track_name).setChecked(prefs.getTitleNamePref());
				menu.findItem(R.id.tool_file_name).setChecked(prefs.getTitleFileNamePref());
				return true;
			case R.id.tool_seq_num:
				prefs.setTitleSeqNumPref(!prefs.getTitleSeqNumPref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_track_name:
				prefs.setTitleNamePref(!prefs.getTitleNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_file_name:
				prefs.setTitleFileNamePref(!prefs.getTitleFileNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_view_subtitle:
				menu = item.getMenu();
				menu.findItem(R.id.tool_sub_track_name).setChecked(prefs.getSubtitleNamePref());
				menu.findItem(R.id.tool_sub_file_name).setChecked(prefs.getSubtitleFileNamePref());
				menu.findItem(R.id.tool_sub_album).setChecked(prefs.getSubtitleAlbumPref());
				menu.findItem(R.id.tool_sub_artist).setChecked(prefs.getSubtitleArtistPref());
				menu.findItem(R.id.tool_sub_dur).setChecked(prefs.getSubtitleDurationPref());
				return true;
			case R.id.tool_sub_track_name:
				prefs.setSubtitleNamePref(!prefs.getSubtitleNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_file_name:
				prefs.setSubtitleFileNamePref(!prefs.getSubtitleFileNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_album:
				prefs.setSubtitleAlbumPref(!prefs.getSubtitleAlbumPref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_artist:
				prefs.setSubtitleArtistPref(!prefs.getSubtitleArtistPref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_dur:
				prefs.setSubtitleDurationPref(!prefs.getSubtitleDurationPref());
				titlePrefChanged(adapter);
				return true;
			default:
				return false;
		}
	}

	private static void titlePrefChanged(MediaLibFragment.ListAdapter adapter) {
		adapter.getParent().updateTitles();
		adapter.reload();
	}

	private static void onSortButtonClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
		f.discardSelection();

		int sort = prefs.getSortByPref();
		OverlayMenu menu = a.getToolBarMenu();
		menu.inflate(R.layout.sort_menu);
		menu.findItem(R.id.tool_sort_name).setChecked(sort == BrowsableItemPrefs.SORT_BY_NAME);
		menu.findItem(R.id.tool_sort_file_name).setChecked(sort == BrowsableItemPrefs.SORT_BY_FILE_NAME);
		menu.findItem(R.id.tool_sort_none).setChecked(sort == BrowsableItemPrefs.SORT_BY_NONE);
		menu.show(ToolBarMediator::onSortMenuItemClick);
	}

	private static boolean onSortMenuItemClick(OverlayMenuItem item) {
		MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
		ActivityFragment mf = a.getActiveFragment();
		if (!(mf instanceof MediaLibFragment)) return false;

		MediaLibFragment f = (MediaLibFragment) mf;

		f.discardSelection();
		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();

		switch (item.getItemId()) {
			case R.id.tool_sort_name:
				prefs.setSortByPref(BrowsableItemPrefs.SORT_BY_NAME);
				sortPrefChanged(adapter);
				return true;
			case R.id.tool_sort_file_name:
				prefs.setSortByPref(BrowsableItemPrefs.SORT_BY_FILE_NAME);
				sortPrefChanged(adapter);
				return true;
			case R.id.tool_sort_none:
				prefs.setSortByPref(BrowsableItemPrefs.SORT_BY_NONE);
				sortPrefChanged(adapter);
				return true;
			default:
				return false;
		}
	}

	private static void sortPrefChanged(MediaLibFragment.ListAdapter adapter) {
		adapter.getParent().updateSorting();
	}
}
