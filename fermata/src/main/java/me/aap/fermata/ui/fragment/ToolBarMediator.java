package me.aap.fermata.ui.fragment;

import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_FILE_NAME;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_NAME;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_NONE;

/**
 * @author Andrey Pavlenko
 */
public class ToolBarMediator implements ToolBarView.Mediator {
	public static final ToolBarView.Mediator instance = BackTitleFilter.instance.join(new ToolBarMediator());

	private ToolBarMediator() {
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		addButton(tb, R.drawable.title, ToolBarMediator::onViewButtonClick, R.id.tool_view);
		addButton(tb, R.drawable.sort, ToolBarMediator::onSortButtonClick, R.id.tool_sort);
	}

	private static void onViewButtonClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		f.discardSelection();
		a.getToolBarMenu().show(b -> {
			b.addItem(R.id.tool_view_title, R.string.title).setSubmenu(ToolBarMediator::buildViewTitleMenu);
			b.addItem(R.id.tool_view_subtitle, R.string.subtitle).setSubmenu(ToolBarMediator::buildViewSubtitleMenu);
		});
	}

	private static void buildViewTitleMenu(OverlayMenu.Builder b) {
		MainActivityDelegate a = MainActivityDelegate.get(b.getMenu().getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
		b.addItem(R.id.tool_seq_num, R.string.seq_num).setChecked(prefs.getTitleSeqNumPref());
		b.addItem(R.id.tool_track_name, R.string.track_name).setChecked(prefs.getTitleNamePref());
		b.addItem(R.id.tool_file_name, R.string.file_name).setChecked(prefs.getTitleFileNamePref());
		b.setSelectionHandler(ToolBarMediator::viewMenuHandler);
	}

	private static void buildViewSubtitleMenu(OverlayMenu.Builder b) {
		MainActivityDelegate a = MainActivityDelegate.get(b.getMenu().getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
		b.addItem(R.id.tool_sub_track_name, R.string.track_name).setChecked(prefs.getSubtitleNamePref());
		b.addItem(R.id.tool_sub_file_name, R.string.file_name).setChecked(prefs.getSubtitleFileNamePref());
		b.addItem(R.id.tool_sub_album, R.string.album).setChecked(prefs.getSubtitleAlbumPref());
		b.addItem(R.id.tool_sub_artist, R.string.artist).setChecked(prefs.getSubtitleArtistPref());
		b.addItem(R.id.tool_sub_dur, R.string.duration).setChecked(prefs.getSubtitleDurationPref());
		b.setSelectionHandler(ToolBarMediator::viewMenuHandler);
	}

	private static boolean viewMenuHandler(OverlayMenuItem item) {
		MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return false;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();

		switch (item.getItemId()) {
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
		adapter.getParent().updateTitles().withMainHandler().thenRun(adapter::reload);
	}

	private static void onSortButtonClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		f.discardSelection();
		a.getToolBarMenu().show(b -> {
			MediaLibFragment.ListAdapter adapter = f.getAdapter();
			BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
			int sort = prefs.getSortByPref();
			b.setSelectionHandler(ToolBarMediator::sortMenuHandler);
			b.addItem(R.id.tool_sort_name, R.string.track_name).setChecked(sort == SORT_BY_NAME, true);
			b.addItem(R.id.tool_sort_file_name, R.string.file_name).setChecked(sort == SORT_BY_FILE_NAME, true);
			b.addItem(R.id.tool_sort_none, R.string.do_not_sort).setChecked(sort == SORT_BY_NONE, true);
		});
	}

	private static boolean sortMenuHandler(OverlayMenuItem item) {
		MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
		ActivityFragment mf = a.getActiveFragment();
		if (!(mf instanceof MediaLibFragment)) return false;

		MediaLibFragment f = (MediaLibFragment) mf;
		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();

		switch (item.getItemId()) {
			case R.id.tool_sort_name:
				prefs.setSortByPref(SORT_BY_NAME);
				sortPrefChanged(adapter);
				return true;
			case R.id.tool_sort_file_name:
				prefs.setSortByPref(SORT_BY_FILE_NAME);
				sortPrefChanged(adapter);
				return true;
			case R.id.tool_sort_none:
				prefs.setSortByPref(SORT_BY_NONE);
				sortPrefChanged(adapter);
				return true;
			default:
				return false;
		}
	}

	private static void sortPrefChanged(MediaLibFragment.ListAdapter adapter) {
		adapter.getParent().updateSorting().thenRun(() -> adapter.setParent(adapter.getParent()));
	}
}
