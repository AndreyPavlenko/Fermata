package me.aap.fermata.ui.fragment;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.engine.MediaEngine.NO_SUBTITLES;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_MASK_NAME_RND;
import static me.aap.fermata.ui.activity.MainActivityPrefs.getGridViewPrefKey;
import static me.aap.fermata.ui.activity.MainActivityPrefs.hasGridViewPref;
import static me.aap.fermata.ui.activity.MainActivityPrefs.hasTextIconSizePref;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.collection.CollectionUtils.filterMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.lib.SearchFolder;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.fermata.ui.view.MediaItemListViewAdapter;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.fermata.ui.view.MediaItemView;
import me.aap.fermata.ui.view.MediaItemWrapper;
import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.function.Function;
import me.aap.utils.holder.Holder;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public abstract class MediaLibFragment extends MainActivityFragment implements MainActivityListener,
		PreferenceStore.Listener, FermataServiceUiBinder.Listener, ToolBarView.Listener {
	private ListAdapter adapter;
	private boolean noScroll;
	private int scrollPosition;
	private Item clicked;

	protected abstract ListAdapter createAdapter(FermataServiceUiBinder b);

	public abstract CharSequence getFragmentTitle();

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarMediator.instance;
	}

	@Override
	public CharSequence getTitle() {
		ListAdapter adapter = getAdapter();
		if (adapter == null) return getFragmentTitle();
		BrowsableItem parent = adapter.getParent();
		if ((parent != null) && (parent.getParent() != null)) return parent.getName();
		else return getFragmentTitle();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.media_items_list_view, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			MediaItemListView v = getListView();
			PreferenceStore ap = a.getPrefs();
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			adapter = createAdapter(b);
			ItemTouchHelper h = new ItemTouchHelper(adapter.getItemTouchCallback());
			adapter.setListView(v);
			v.setAdapter(adapter);
			h.attachToRecyclerView(v);
			ap.addBroadcastListener(v);
			ap.addBroadcastListener(this);
			a.addBroadcastListener(this);
			a.getToolBar().addBroadcastListener(this);
			b.getLib().getPrefs().addBroadcastListener(this);
			b.addBroadcastListener(this);
			Log.d("MediaLibFragment view created: ", this);
		});
	}

	@Override
	public void onDestroyView() {
		scrollPosition = -1;
		cleanUp(getMainActivity());
		super.onDestroyView();
		Log.d("MediaLibFragment view destroyed: ", this);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		scrollToPosition();
	}

	private void cleanUp(MainActivityDelegate a) {
		Log.d("Cleaning up fragment: ", this);
		MediaItemListView v = (MediaItemListView) getView();
		PreferenceStore ap = a.getPrefs();
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		ap.removeBroadcastListener(this);
		a.removeBroadcastListener(this);
		a.getToolBar().removeBroadcastListener(this);
		b.getLib().getPrefs().removeBroadcastListener(this);
		b.removeBroadcastListener(this);
		if (v != null) ap.removeBroadcastListener(v);
		if (adapter != null) adapter.onDestroy();
	}

	@NonNull
	public MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@NonNull
	public FutureSupplier<MainActivityDelegate> getMainActivityDelegate() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	@NonNull
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

	public void openItem(BrowsableItem folder) {
		if (isHidden()) return;
		ListAdapter a = getAdapter();
		if (!folder.equals(a.getParent())) a.setParent(folder);
	}

	public void revealItem(Item i) {
		if (isHidden()) return;
		ListAdapter a = getAdapter();
		BrowsableItem p = i.getParent();
		if (p == null) return;
		if (!p.equals(a.getParent())) a.setParent(p);
		// Make sure the list is loaded
		p.getChildren().main(getMainActivity().getHandler()).onSuccess(l -> getListView().focusTo(i));
	}

	public boolean onBackPressed() {
		MainActivityDelegate ad = getMainActivity();
		BodyLayout b = ad.getBody();

		if (b.isVideoMode()) {
			b.setMode(BodyLayout.Mode.BOTH);
			return true;
		}

		ListAdapter a = getAdapter();
		BrowsableItem oldParent = a.getParent();
		if (oldParent == null) return false;
		BrowsableItem newParent = oldParent.getParent();
		if (newParent == null) return false;
		a.setParent(newParent);
		ad.post(() -> revealItem(oldParent));
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
	public void onDurationChanged(PlayableItem i) {
		for (MediaItemWrapper w : getAdapter().getList()) {
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
			b.addItem(R.id.nav_select_all, me.aap.utils.R.drawable.check_box, R.string.select_all);
			b.addItem(R.id.nav_unselect_all, me.aap.utils.R.drawable.check_box_blank, R.string.unselect_all);
		} else {
			b.addItem(R.id.nav_select, me.aap.utils.R.drawable.check_box, R.string.select);
		}
	}

	public void contributeToContextMenu(OverlayMenu.Builder builder, MediaItemMenuHandler handler) {
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

	public boolean isGridSupported() {
		return (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) ||
				!getActivityDelegate().getBody().isBothMode();
	}

	public int getSupportedSortOpts() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) ? SORT_MASK_NAME_RND : p.getSupportedSortOpts();
	}

	protected boolean isSupportedItem(Item i) {
		return false;
	}

	@SuppressWarnings("unchecked")
	public <A extends ListAdapter> A getAdapter() {
		return (A) adapter;
	}

	@NonNull
	public MediaItemListView getListView() {
		return (MediaItemListView) requireView();
	}

	public void discardSelection() {
		getAdapter().getListView().discardSelection();
	}

	private void scrollToPosition() {
		getMainActivityDelegate().onSuccess(a -> a.post(() -> {
			int pos = scrollPosition;
			if (pos == -1) return;
			getListView().smoothScrollToPosition(pos);
		}));
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
		if (e == MODE_CHANGED) {
			if (isHidden()) return;
			MainActivityPrefs store = a.getPrefs();
			if (!store.getGridViewPref(a)) return;
			List<PreferenceStore.Pref<?>> prefs = singletonList(getGridViewPrefKey(a));
			store.fireBroadcastEvent(l -> l.onPreferenceChanged(store, prefs));
		} else if (e == ACTIVITY_DESTROY) {
			cleanUp(a);
		}
	}

	@Override
	public void onToolBarEvent(ToolBarView tb, byte event) {
		try {
			noScroll = true;
			adapter.setFilter(tb.getFilter().getText().toString());
		} finally {
			noScroll = false;
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		MainActivityDelegate a = getMainActivity();
		boolean viewChanged = hasGridViewPref(a, prefs);

		if (viewChanged || hasTextIconSizePref(getMainActivity(), prefs)) {
			MediaItemListView list = (MediaItemListView) getView();

			if (list != null) {
				Context ctx = getContext();
				boolean grid = a.isGridView();
				float size = a.getPrefs().getTextIconSizePref(a);

				for (MediaItemWrapper w : getAdapter().getList()) {
					MediaItemView v = w.getView();
					if (v == null) continue;
					if (viewChanged) v.applyLayout(ctx, grid, size);
					else v.setSize(ctx, grid, size);
				}
			}

			return;
		}

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

	@Override
	public boolean isVoiceCommandsSupported() {
		return true;
	}

	public FutureSupplier<BrowsableItem> findFolder(String name) {
		Holder<BrowsableItem> found = new Holder<>();
		return findFolder(getAdapter().getRoot(), name, found);
	}

	private FutureSupplier<BrowsableItem> findFolder(BrowsableItem folder, String name,
																									 Holder<BrowsableItem> found) {
		if (found.value != null) return completed(found.value);
		return folder.getUnsortedChildren().then(list -> {
			List<BrowsableItem> folders = new ArrayList<>();

			for (Item i : list) {
				if (!(i instanceof BrowsableItem) || (i instanceof StreamItem)) continue;
				if (name.equalsIgnoreCase(i.getName())) return completed(found.value = (BrowsableItem) i);
				else folders.add((BrowsableItem) i);
			}

			if (folders.isEmpty()) return completedNull();
			Iterator<BrowsableItem> it = folders.iterator();
			return Async.iterate(() -> it.hasNext() ? findFolder(it.next(), name, found) : null);
		});
	}

	public void openFolder(BrowsableItem folder) {
		getAdapter().setParent(folder, true, true);
	}

	public void play() {
		playFolder(getAdapter().getParent());
	}

	public void playFolder(BrowsableItem folder) {
		openFolder(folder);
		MainActivityDelegate a = getMainActivity();
		folder.getLastPlayedItem()
				.then(last -> (last == null) ? folder.getFirstPlayable() : completed(last))
				.main(a.getHandler())
				.onSuccess(p -> {
					if (p == null) return;
					a.getMediaServiceBinder().playItem(p);
					a.goToItem(p);
				});
	}

	@Override
	public void voiceCommand(VoiceCommand cmd) {
		BrowsableItem parent = getAdapter().getParent();
		if (parent == null) return;
		MainActivityDelegate a = getMainActivity();
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		Function<List<PlayableItem>, BrowsableItem> ps = items -> {
			PlayableItem cur = b.getCurrentItem();
			return ((cur == null) || !items.contains(cur)) ? parent : cur.getParent();
		};

		boolean play = cmd.isPlay();
		SearchFolder.search(cmd.getQuery(), ps).main(a.getHandler()).onSuccess(f -> {
			if (f == null) return;
			List<PlayableItem> items = f.getItemsFound();
			if (items.isEmpty()) return;
			PlayableItem first = items.get(0);
			if (play) b.playItem(first);
			if (items.size() == 1) a.goToItem(first);
			else getAdapter().setParent(f);
			if (!play) a.post(() -> getListView().focusTo(first));
		});
	}

	public class ListAdapter extends MediaItemListViewAdapter {

		public ListAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity);
			super.setParent(parent, false);
		}

		@Override
		@SuppressLint("MissingSuperCall")
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return setParent(parent, userAction, true);
		}

		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction, boolean scroll) {
			BrowsableItem prev = super.getParent();
			boolean same = parent == prev;
			MediaItemListView list = scroll && same ? getListView() : null;
			int scrollPos = (list != null) ? list.getScrollPosition() : 0;
			FutureSupplier<?> set = super.setParent(parent, userAction);

			if (!isHidden() && !noScroll) {
				getMainActivity().fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);

				if (scroll && (parent != null)) {
					if (!same && set.isDone()) {
						scrollToPrev(prev);
					} else {
						scrollPosition = scrollPos;
						scrollToPosition();
						if (!same) set.onSuccess(v -> scrollToPrev(prev));
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
				if (!((PlayableItem) i).isVideo()) {
					MainActivityDelegate a = getActivityDelegate();
					MediaEngine eng = a.getMediaSessionCallback().getEngine();
					if ((eng != null) && (eng.getSource() == i) &&
							(eng.getCurrentSubtitles() != NO_SUBTITLES)) {
						a.showFragment(R.id.subtitles_fragment);
						return;
					}
				}

				if (i instanceof StreamItem) {
					if (clicked == i) {
						clicked = null;
						openEpg((StreamItem) i);
					} else {
						clicked = i;
						App.get().getHandler().postDelayed(() -> {
							boolean same = clicked == i;
							clicked = null;
							if (same) onClick((PlayableItem) i);
						}, 300);
					}
				} else if (i instanceof ArchiveItem) {
					clicked = null;
					if (!((ArchiveItem) i).isExpired()) onClick((PlayableItem) i);
				} else {
					clicked = null;
					onClick((PlayableItem) i);
				}
			} else {
				clicked = null;
				super.onClick(v);
			}
		}

		public void openEpg(StreamItem i) {
			setParent(i, true, false).thenRun(() -> {
				long time = System.currentTimeMillis();
				List<MediaItemWrapper> l = getAdapter().getList();
				int pos = 0;

				for (MediaItemWrapper w : l) {
					if (w.getItem() instanceof EpgItem) {
						EpgItem e = (EpgItem) w.getItem();
						if ((e.getStartTime() <= time) && (e.getEndTime() > time)) {
							scrollPosition = pos;
							getMainActivityDelegate().onSuccess(a -> a.post(() -> {
								if (getParent() != i) return;
								getListView().smoothScrollToPosition(scrollPosition);
								getListView().focusTo(e);
							}));
							break;
						}
					}
					pos++;
				}
			});
		}

		private void onClick(PlayableItem i) {
			MainActivityDelegate a = getMainActivity();

			if (i.isVideo() && !a.getBody().getVideoView().isSurfaceCreated() &&
					!a.getMediaSessionCallback().hasCustomEngineProvider()) {
				a.getBody().setMode(BodyLayout.Mode.VIDEO);
				a.getBody().getVideoView().onSurfaceCreated(() -> onClick(i));
				return;
			}

			FermataServiceUiBinder b = a.getMediaServiceBinder();
			PlayableItem cur = b.getCurrentItem();
			b.playItem(i);
			MediaEngine eng = b.getCurrentEngine();
			if (i.equals(cur) && (eng != null) && eng.isVideoModeRequired())
				a.getBody().setMode(BodyLayout.Mode.VIDEO);
		}

		@Override
		protected void setChildren(List<? extends Item> children) {
			super.setChildren(children);
			if (noScroll) return;
			BrowsableItem p = getParent();
			PlayableItem current = getMainActivityDelegate()
					.mapIfNotNull(MainActivityDelegate::getCurrentPlayable).peek();

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
