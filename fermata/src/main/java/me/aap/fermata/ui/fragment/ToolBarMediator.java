package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.FOCUS_UP;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_DATE;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_FILE_NAME;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_NAME;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_NONE;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_BY_RND;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataToolAddon;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
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
		addButton(tb, R.drawable.title, ToolBarMediator::onViewButtonClick, R.id.tool_view);
		addButton(tb, R.drawable.sort, ToolBarMediator::onSortButtonClick, R.id.tool_sort);

		if ((f instanceof MediaLibFragment) && ((MediaLibFragment) f).isGridSupported()) {
			int gridIcon = a.isGridView() ? R.drawable.view_list : R.drawable.view_grid;
			addButton(tb, gridIcon, ToolBarMediator::onGridButtonClick, R.id.tool_grid);
		}

		if ((f instanceof MediaLibFragment) && a.getPrefs().getShowPgUpDownPref(a)) {
			addButton(tb, R.drawable.pg_down, ToolBarMediator::onPgUpDownButtonClick, R.id.tool_pg_down, LEFT);
			addButton(tb, R.drawable.pg_up, ToolBarMediator::onPgUpDownButtonClick, R.id.tool_pg_up, LEFT);
		} else {
			tb.findViewById(me.aap.utils.R.id.tool_bar_back_button);
		}

		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataToolAddon) {
				((FermataToolAddon) addon).contributeTool(this, tb, f);
			}
		}

		setButtonsVisibility(tb, f);
		int n = tb.getChildCount();

		if (n > 1) {
			View first = tb.getChildAt(0);
			View last = tb.getChildAt(n - 1);
			last.setNextFocusRightId(first.getId());
			first.setNextFocusLeftId(last.getId());
		}
	}

	@Override
	public void onActivityEvent(ToolBarView view, ActivityDelegate a, long e) {
		ToolBarView.Mediator.BackTitleFilter.super.onActivityEvent(view, a, e);

		if ((e == FRAGMENT_CHANGED) || e == FRAGMENT_CONTENT_CHANGED) {
			ActivityFragment f = a.getActiveFragment();
			if (f != null) setButtonsVisibility(view, f);
		}
	}

	@Nullable
	@Override
	public View focusSearch(ToolBarView tb, View focused, int direction) {
		View v = ToolBarView.Mediator.BackTitleFilter.super.focusSearch(tb, focused, direction);
		if (v != null) return v;

		if (direction == FOCUS_UP) {
			Context ctx = tb.getContext();
			MainActivityDelegate a = MainActivityDelegate.get(ctx);
			ControlPanelView p = a.getControlPanel();
			return (isVisible(p)) ? p.focusSearch() : MediaItemListView.focusSearchLast(ctx, focused);
		} else if (direction == FOCUS_DOWN) {
			int id = focused.getId();
			Context ctx = tb.getContext();
			if (id == me.aap.utils.R.id.tool_bar_back_button) {
				return MediaItemListView.focusSearchFirst(ctx, focused);
			} else {
				return MediaItemListView.focusSearchFirstVisible(ctx, focused);
			}
		}

		return null;
	}

	private void setButtonsVisibility(ToolBarView tb, ActivityFragment f) {
		if (!(f instanceof MediaLibFragment)) return;
		MediaLibFragment.ListAdapter a = ((MediaLibFragment) f).getAdapter();
		if (a == null) return;
		BrowsableItem b = a.getParent();

		if ((b == null) || (b == b.getRoot()) || (b instanceof StreamItem)) {
			setButtonVisibility(tb, R.id.tool_view, GONE);
			setButtonVisibility(tb, R.id.tool_sort, GONE);
			setButtonVisibility(tb, R.id.tool_grid, (b instanceof StreamItem) ? GONE : VISIBLE);
		} else {
			setButtonVisibility(tb, R.id.tool_view, VISIBLE);
			setButtonVisibility(tb, R.id.tool_grid, VISIBLE);
			setButtonVisibility(tb, R.id.tool_sort, b.sortChildrenEnabled() ? VISIBLE : GONE);
		}
	}

	private static void setButtonVisibility(ToolBarView tb, @IdRes int id, int visibility) {
		View v = tb.findViewById(id);
		if (v != null) v.setVisibility(visibility);
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

	private static void onPgUpDownButtonClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		MediaItemListView lv = f.getListView();
		if (v.getId() == R.id.tool_pg_up) lv.pageUp();
		else lv.pageDown();
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
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		MainActivityPrefs prefs = a.getPrefs();
		boolean grid = a.isGridView();
		((ImageButton) v).setImageResource(grid ? R.drawable.view_grid : R.drawable.view_list);
		prefs.setGridViewPref(a, !grid);
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
			int m = f.getSupportedSortOpts();
			b.setSelectionHandler(ToolBarMediator::sortMenuHandler);
			addSortItem(b, R.id.tool_sort_name, R.string.track_name, SORT_BY_NAME, sort, m);
			addSortItem(b, R.id.tool_sort_file_name, R.string.file_name, SORT_BY_FILE_NAME, sort, m);
			addSortItem(b, R.id.tool_sort_date, R.string.date, SORT_BY_DATE, sort, m);
			addSortItem(b, R.id.tool_sort_random, R.string.random, SORT_BY_RND, sort, m);
			addSortItem(b, R.id.tool_sort_none, R.string.do_not_sort, SORT_BY_NONE, sort, m);

			if ((sort != SORT_BY_NONE) && (sort != SORT_BY_RND)) {
				b.addItem(R.id.tool_sort_desc, R.string.descending).setChecked(prefs.getSortDescPref());
			}
		});
	}

	private static void addSortItem(OverlayMenu.Builder b, @IdRes int id, @StringRes int title, int type, int cur, int m) {
		if ((m & (1 << type)) != 0) b.addItem(id, title).setChecked(type == cur, true);
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
			p.updateSorting().main().thenRun(() -> p.getPrefs().setSortDescPref(!p.getPrefs().getSortDescPref()));
			return true;
		}
		return false;
	}

	private static void setSortBy(MediaLibFragment.ListAdapter adapter, int sortBy) {
		BrowsableItem p = adapter.getParent();
		p.updateSorting().main().thenRun(() -> p.getPrefs().setSortByPref(sortBy));
	}
}
