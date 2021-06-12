package me.aap.fermata.ui.fragment;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ImageButton;
import me.aap.utils.ui.view.ToolBarView;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.FOCUS_UP;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_DATE;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_FILE_NAME;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_NAME;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_NONE;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_RND;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public class ToolBarMediator implements ToolBarView.Mediator.BackTitleFilter {
	public static final ToolBarView.Mediator instance = new ToolBarMediator();

	private ToolBarMediator() {
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		ToolBarView.Mediator.BackTitleFilter.super.enable(tb, f);
		MainActivityDelegate a = MainActivityDelegate.get(tb.getContext());
		View last = null;
		addButton(tb, R.drawable.title, ToolBarMediator::onViewButtonClick, R.id.tool_view);
		View sort = addButton(tb, R.drawable.sort, ToolBarMediator::onSortButtonClick, R.id.tool_sort);

		if ((f instanceof MediaLibFragment) && ((MediaLibFragment) f).isGreedSupported()) {
			int gridIcon = a.isGridView() ? R.drawable.view_list : R.drawable.view_grid;
			last = addButton(tb, gridIcon, ToolBarMediator::onGridButtonClick, R.id.tool_grid);
		}

		if (last == null) last = sort;
		View first = tb.findViewById(R.id.tool_bar_back_button);
		last.setNextFocusRightId(R.id.tool_bar_back_button);
		first.setNextFocusLeftId(R.id.tool_grid);
		setSortButtonVisibility(tb, f);
	}

	@Override
	public void onActivityEvent(ToolBarView view, ActivityDelegate a, long e) {
		ToolBarView.Mediator.BackTitleFilter.super.onActivityEvent(view, a, e);

		if ((e == FRAGMENT_CHANGED) || e == FRAGMENT_CONTENT_CHANGED) {
			ActivityFragment f = a.getActiveFragment();
			if (f != null) setSortButtonVisibility(view, f);
		}
	}

	@Nullable
	@Override
	public View focusSearch(ToolBarView tb, View focused, int direction) {
		View v = ToolBarView.Mediator.BackTitleFilter.super.focusSearch(tb, focused, direction);
		if (v != null) return v;

		if (direction == FOCUS_UP) {
			MainActivityDelegate a = MainActivityDelegate.get(tb.getContext());
			ControlPanelView p = a.getControlPanel();
			return (isVisible(p)) ? p.focusSearch() : MediaItemListView.focusLast(focused);
		} else if (direction == FOCUS_DOWN) {
			return MediaItemListView.focusFirst(focused);
		}

		return null;
	}

	private void setSortButtonVisibility(ToolBarView tb, ActivityFragment f) {
		if (!(f instanceof MediaLibFragment)) return;
		MediaLibFragment.ListAdapter a = ((MediaLibFragment) f).getAdapter();
		if (a == null) return;
		BrowsableItem b = a.getParent();
		tb.findViewById(R.id.tool_sort).setVisibility(b.sortChildrenEnabled() ? VISIBLE : GONE);
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

		int itemId = item.getItemId();
		if (itemId == R.id.tool_seq_num) {
			prefs.setTitleSeqNumPref(!prefs.getTitleSeqNumPref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_track_name) {
			prefs.setTitleNamePref(!prefs.getTitleNamePref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_file_name) {
			prefs.setTitleFileNamePref(!prefs.getTitleFileNamePref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_sub_track_name) {
			prefs.setSubtitleNamePref(!prefs.getSubtitleNamePref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_sub_file_name) {
			prefs.setSubtitleFileNamePref(!prefs.getSubtitleFileNamePref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_sub_album) {
			prefs.setSubtitleAlbumPref(!prefs.getSubtitleAlbumPref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_sub_artist) {
			prefs.setSubtitleArtistPref(!prefs.getSubtitleArtistPref());
			titlePrefChanged(adapter);
			return true;
		} else if (itemId == R.id.tool_sub_dur) {
			prefs.setSubtitleDurationPref(!prefs.getSubtitleDurationPref());
			titlePrefChanged(adapter);
			return true;
		}
		return false;
	}

	private static void titlePrefChanged(MediaLibFragment.ListAdapter adapter) {
		adapter.getParent().updateTitles().main().thenRun(adapter::reload);
	}

	private static void onGridButtonClick(View v) {
		MainActivityPrefs prefs = MainActivityDelegate.get(v.getContext()).getPrefs();
		boolean grid = prefs.getGridViewPref();
		((ImageButton) v).setImageResource(grid ? R.drawable.view_grid : R.drawable.view_list);
		prefs.setGridViewPref(!grid);
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
			b.addItem(R.id.tool_sort_date, R.string.date).setChecked(sort == SORT_BY_DATE, true);
			b.addItem(R.id.tool_sort_random, R.string.random).setChecked(sort == SORT_BY_RND, true);
			b.addItem(R.id.tool_sort_none, R.string.do_not_sort).setChecked(sort == SORT_BY_NONE, true);

			if ((sort != SORT_BY_NONE) && (sort != SORT_BY_RND)) {
				b.addItem(R.id.tool_sort_desc, R.string.descending).setChecked(prefs.getSortDescPref());
			}
		});
	}

	private static boolean sortMenuHandler(OverlayMenuItem item) {
		MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
		ActivityFragment mf = a.getActiveFragment();
		if (!(mf instanceof MediaLibFragment)) return false;

		MediaLibFragment f = (MediaLibFragment) mf;
		MediaLibFragment.ListAdapter adapter = f.getAdapter();

		int itemId = item.getItemId();
		if (itemId == R.id.tool_sort_name) {
			setSortBy(adapter, SORT_BY_NAME);
			return true;
		} else if (itemId == R.id.tool_sort_file_name) {
			setSortBy(adapter, SORT_BY_FILE_NAME);
			return true;
		} else if (itemId == R.id.tool_sort_date) {
			setSortBy(adapter, SORT_BY_DATE);
			return true;
		} else if (itemId == R.id.tool_sort_random) {
			setSortBy(adapter, SORT_BY_RND);
			return true;
		} else if (itemId == R.id.tool_sort_none) {
			setSortBy(adapter, SORT_BY_NONE);
			return true;
		} else if (itemId == R.id.tool_sort_desc) {
			BrowsableItem p = adapter.getParent();
			p.updateSorting().main()
					.thenRun(() -> p.getPrefs().setSortDescPref(!p.getPrefs().getSortDescPref()));
			return true;
		}
		return false;
	}

	private static void setSortBy(MediaLibFragment.ListAdapter adapter, int sortBy) {
		BrowsableItem p = adapter.getParent();
		p.updateSorting().main().thenRun(() -> p.getPrefs().setSortByPref(sortBy));
	}
}
