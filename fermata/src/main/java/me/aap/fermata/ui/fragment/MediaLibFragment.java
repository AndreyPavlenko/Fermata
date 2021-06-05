package me.aap.fermata.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.fermata.ui.view.MediaItemListViewAdapter;
import me.aap.fermata.ui.view.MediaItemView;
import me.aap.fermata.ui.view.MediaItemWrapper;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ToolBarView;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.collection.CollectionUtils.filterMap;
import static me.aap.utils.misc.Assert.assertTrue;

/**
 * @author Andrey Pavlenko
 */
public abstract class MediaLibFragment extends MainActivityFragment implements MainActivityListener,
		PreferenceStore.Listener, FermataServiceUiBinder.Listener, ToolBarView.Listener {
	private ListAdapter adapter;
	private int scrollPosition;

	protected abstract ListAdapter createAdapter(FermataServiceUiBinder b);

	public abstract CharSequence getFragmentTitle();

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FloatingButtonMediator.instance;
	}

	@Override
	public CharSequence getTitle() {
		ListAdapter adapter = getAdapter();
		if (adapter == null) return getFragmentTitle();

		BrowsableItem parent = adapter.getParent();
		if ((parent != null) && (parent.getParent() != null)) return parent.getName();
		else return getFragmentTitle();
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.media_items_list_view, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		MainActivityDelegate a = getMainActivity();
		a.getToolBar().addBroadcastListener(this);

		if (adapter != null) {
			attachTouchHelper();
		} else {
			FermataServiceUiBinder b = a.getMediaServiceBinder();

			if (b != null) {
				bind(b);
				a.addBroadcastListener(this, ACTIVITY_FINISH);
			} else {
				a.addBroadcastListener(this, SERVICE_BOUND | ACTIVITY_FINISH);
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		MainActivityDelegate a = getMainActivity();
		if (a == null) return;

		a.removeBroadcastListener(this);
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		if (b == null) return;

		b.removeBroadcastListener(this);
		a.getPrefs().removeBroadcastListener(this);
		b.getLib().getPrefs().removeBroadcastListener(this);
	}

	private void bind(FermataServiceUiBinder b) {
		assertTrue(adapter == null);
		adapter = createAdapter(b);
		b.addBroadcastListener(this);
		b.getLib().getPrefs().addBroadcastListener(this);
		getMainActivity().getPrefs().addBroadcastListener(this);
		attachTouchHelper();
	}

	private void attachTouchHelper() {
		MediaItemListView listView = getListView();
		assert listView != null;
		assertTrue(adapter != null);
		adapter.setListView(listView);
		listView.setAdapter(adapter);
		ItemTouchHelper touchHelper = new ItemTouchHelper(adapter.getItemTouchCallback());
		touchHelper.attachToRecyclerView(listView);
	}

	public MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	public MediaLib getLib() {
		return getMainActivity().getLib();
	}

	@Override
	public boolean isRootPage() {
		ListAdapter a = getAdapter();
		if (a == null) return true;

		BrowsableItem p = a.getParent();
		return (p == null) || (p.getParent() == null);
	}

	public void revealItem(Item i) {
		ListAdapter a = getAdapter();
		BrowsableItem p = i.getParent();
		if (p == null) return;

		if (!p.equals(a.getParent())) a.setParent(p);

		// Make sure the list is loaded
		p.getChildren().main().onSuccess(l -> {
			scrollPosition = indexOf(a.getList(), i);
			FermataApplication.get().getHandler().post(this::scrollToPosition);
		});
	}

	public boolean onBackPressed() {
		MainActivityDelegate ad = getMainActivity();
		if (ad == null) return false;
		BodyLayout b = ad.getBody();

		if (b.isVideoMode()) {
			b.setMode(BodyLayout.Mode.BOTH);
			return true;
		}

		ListAdapter a = getAdapter();
		BrowsableItem p = a.getParent();
		if (p == null) return false;
		p = p.getParent();
		if (p == null) return false;
		a.setParent(p);
		return true;
	}

	@Override
	public void onRefresh(BooleanConsumer refreshing) {
		reload().onCompletion((r, f) -> refreshing.accept(false));
	}

	public FutureSupplier<?> reload() {
		discardSelection();
		return getAdapter().reload();
	}

	public FutureSupplier<?> refresh() {
		getLib().getVfsManager().clearCache();
		return getAdapter().getParent().refresh().main().thenRun(this::reload);
	}

	public void rescan() {
		getLib().getVfsManager().clearCache();
		getAdapter().getParent().rescan().main().thenRun(this::reload);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) scrollToPosition();
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		scrollPosition = -1;
		MediaItemListView view = getListView();
		if (view == null) return;

		ListAdapter a = getAdapter();
		BrowsableItem p = a.getParent();
		if (p == null) return;
		List<MediaItemWrapper> list = a.getList();

		if ((oldItem != null) && p.equals(oldItem.getParent())) {
			if ((newItem != null) && isSupportedItem(newItem)) {
				BrowsableItem newParent = newItem.getParent();

				if (p.equals(newParent)) {
					scrollPosition = indexOf(list, newItem);
				} else {
					a.setParent(newParent);
					scrollPosition = indexOf(a.getList(), newItem);
				}
			} else {
				scrollPosition = indexOf(list, oldItem);
			}
		}

		if (!isHidden()) {
			scrollToPosition();
			a.getListView().refreshState();
		}
	}

	@Override
	public void durationChanged(PlayableItem i) {
		ListAdapter a = getAdapter();
		if (a == null) return;

		for (MediaItemWrapper w : a.getList()) {
			if (i.equals(w.getItem())) {
				MediaItemView v = w.getView();
				if (v != null) v.refresh();
				break;
			}
		}
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);

		OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
		if (isRefreshSupported()) b.addItem(R.id.refresh, R.drawable.refresh, R.string.refresh);
		if (isRescanSupported()) b.addItem(R.id.rescan, R.drawable.loading, R.string.rescan);

		ListAdapter a = getAdapter();
		if (!a.hasSelectable()) return;

		if (a.getListView().isSelectionActive()) {
			b.addItem(R.id.nav_select_all, R.drawable.check_box, R.string.select_all);
			b.addItem(R.id.nav_unselect_all, R.drawable.check_box_blank, R.string.unselect_all);
		} else {
			b.addItem(R.id.nav_select, R.drawable.check_box, R.string.select);
		}
	}

	protected boolean navBarMenuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();

		if (itemId == R.id.nav_select || itemId == R.id.nav_select_all) {
			getAdapter().getListView().select(true);
			return true;
		} else if (itemId == R.id.nav_unselect_all) {
			getAdapter().getListView().select(false);
			return true;
		} else if (itemId == R.id.refresh) {
			refresh();
			return true;
		} else if (itemId == R.id.rescan) {
			rescan();
			return true;
		} else if (itemId == R.id.favorites_add) {
			requireNonNull(getLib()).getFavorites().addItems(filterMap(getAdapter().getList(),
					MediaItemWrapper::isSelected, (i, w, l) -> l.add((PlayableItem) w.getItem()),
					ArrayList::new));
			discardSelection();
			MediaLibFragment f = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
			if (f != null) f.reload();
			return true;
		}

		return false;
	}

	protected boolean isRefreshSupported() {
		return false;
	}

	protected boolean isRescanSupported() {
		return false;
	}

	protected boolean isSupportedItem(Item i) {
		return false;
	}

	@SuppressWarnings("unchecked")
	public <A extends ListAdapter> A getAdapter() {
		return (A) adapter;
	}

	@Nullable
	public MediaItemListView getListView() {
		View v = getView();
		return (v == null) ? null : getView().findViewById(R.id.media_items_list_view);
	}

	public void discardSelection() {
		getAdapter().getListView().discardSelection();
	}

	private void scrollToPosition() {
		FermataApplication.get().getHandler().post(() -> {
			int pos = scrollPosition;
			if (pos == -1) return;
			MediaItemListView list = getListView();
			if (list == null) return;
			list.smoothScrollToPosition(pos);
		});
	}

	private static final Set<PreferenceStore.Pref<?>> reloadOnPrefChange = new HashSet<>(Arrays.asList(
			BrowsableItemPrefs.TITLE_SEQ_NUM,
			BrowsableItemPrefs.TITLE_NAME,
			BrowsableItemPrefs.TITLE_FILE_NAME,
			BrowsableItemPrefs.SUBTITLE_NAME,
			BrowsableItemPrefs.SUBTITLE_FILE_NAME,
			BrowsableItemPrefs.SUBTITLE_ALBUM,
			BrowsableItemPrefs.SUBTITLE_ARTIST,
			BrowsableItemPrefs.SUBTITLE_DURATION,
			BrowsableItemPrefs.SORT_BY,
			BrowsableItemPrefs.SORT_DESC
	));

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityFinishEvent(a, e) || handleActivityDestroyEvent(a, e)) return;
		if (e == SERVICE_BOUND) bind(a.getMediaServiceBinder());
	}

	@Override
	public void onToolBarEvent(ToolBarView tb, byte event) {
		adapter.setFilter(tb.getFilter().getText().toString());
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		BrowsableItem p = getAdapter().getParent();
		if (p == null) return;

		if (prefs.contains(BrowsableItemPrefs.SHOW_TRACK_ICONS)) {
			getAdapter().reload();
			return;
		}

		if (!store.equals(p.getPrefs())) return;
		if (prefs.contains(BrowsableItemPrefs.SHOW_TRACK_ICONS)) p.getRoot().updateTitles();
		if (!Collections.disjoint(reloadOnPrefChange, prefs)) getAdapter().reload();
	}

	public class ListAdapter extends MediaItemListViewAdapter {

		public ListAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity);
			super.setParent(parent, false);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			BrowsableItem prev = super.getParent();
			FutureSupplier<?> set = super.setParent(parent, userAction);

			if (!isHidden()) {
				getMainActivity().fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);

				if (parent != null) {
					if (set.isDone()) {
						scrollToPrev(prev);
					} else {
						scrollPosition = 0;
						scrollToPosition();
						set.onSuccess(v -> scrollToPrev(prev));
					}
				}
			}

			return set;
		}

		private void scrollToPrev(BrowsableItem prev) {
			int idx = indexOf(getList(), prev);
			if (idx != -1) scrollPosition = idx;
			else if (scrollPosition == -1) scrollPosition = 0;
			scrollToPosition();
		}

		@Override
		public void onClick(View v) {
			Item i = ((MediaItemView) v).getItem();
			discardSelection();

			if (i instanceof PlayableItem) {
				MainActivityDelegate a = getMainActivity();
				FermataServiceUiBinder b = a.getMediaServiceBinder();
				PlayableItem cur = b.getCurrentItem();
				b.playItem((PlayableItem) i);
				if (i.equals(cur)) a.getBody().setMode(BodyLayout.Mode.VIDEO);
			} else {
				super.onClick(v);
			}
		}

		@Override
		protected void setChildren(List<? extends Item> children) {
			super.setChildren(children);
			BrowsableItem p = getParent();
			PlayableItem current = getMainActivity().getCurrentPlayable();

			if ((current != null) && current.getParent().equals(p)) {
				scrollPosition = indexOf(getList(), current);
				if (!isHidden()) scrollToPosition();
			} else {
				p.getLastPlayedItem().main().onSuccess(last -> {
					scrollPosition = (last != null) ? indexOf(getList(), last) : 0;
					if (scrollPosition == -1) scrollPosition = 0;
					if (!isHidden()) scrollToPosition();
				});
			}
		}
	}

	private static int indexOf(List<MediaItemWrapper> list, Item item) {
		int size = list.size();
		for (int i = 0; i < size; i++) {
			if (item.equals(list.get(i).getItem())) return i;
		}
		return -1;
	}
}
