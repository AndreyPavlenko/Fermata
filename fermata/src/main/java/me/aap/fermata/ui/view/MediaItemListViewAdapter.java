package me.aap.fermata.ui.view;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.filterMap;
import static me.aap.utils.concurrent.ConcurrentUtils.ensureMainThread;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.support.v4.media.MediaDescriptionCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.ItemBase;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.view.MovableRecyclerViewAdapter;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemListViewAdapter extends MovableRecyclerViewAdapter<MediaItemViewHolder>
		implements OnClickListener, Item.ChangeListener {
	private final MainActivityDelegate activity;
	private BrowsableItem parent;
	private String filterText = "";
	private Pattern filter;
	private MediaItemListView listView;
	private List<MediaItemWrapper> list = Collections.emptyList();

	public MediaItemListViewAdapter(MainActivityDelegate activity) {
		this.activity = activity;
	}

	@NonNull
	public MediaItemListView getListView() {
		return listView;
	}

	public void setListView(@NonNull MediaItemListView listView) {
		this.listView = listView;
	}

	public BrowsableItem getParent() {
		return parent;
	}

	public BrowsableItem getRoot() {
		BrowsableItem p = getParent();
		return (p == null) ? null : p.getRoot();
	}

	public FutureSupplier<?> setParent(BrowsableItem parent) {
		return setParent(parent, true);
	}

	@CallSuper
	public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
		ensureMainThread(true);
		if (this.parent != null) this.parent.removeChangeListener(this);
		this.parent = parent;
		list = Collections.emptyList();
		notifyChanged();
		if (parent == null) return completedVoid();
		parent.addChangeListener(this);

		FutureSupplier<?> f = parent.getChildren().main()
				.addConsumer((result, fail, progress, total) -> {
					if (this.parent != parent) return;

					if (fail != null) {
						if (isCancellation(fail)) return;
						Log.e(fail, "Failed to load children");
						UiUtils.showAlert(activity.getContext(), fail.getLocalizedMessage());
					} else {
						setChildren(result);
					}
				});

		if (userAction) activity.setContentLoading(f);
		return f;
	}

	@CallSuper
	protected void setChildren(List<? extends Item> children) {
		ensureMainThread(true);
		list = filterMap(children, this::filter, (i, c, l) -> l.add(new MediaItemWrapper(c)), ArrayList::new);
		notifyChanged();
	}

	public void setFilter(String filter) {
		if (!filter.equals(filterText)) {
			filterText = filter;
			this.filter = filter.isEmpty() ? null : Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE);
			setParent(getParent());
		}
	}

	public FutureSupplier<?> reload() {
		getListView().discardSelection();
		return setParent(getParent());
	}

	public void refresh() {
		getListView().refresh();
	}

	public List<MediaItemWrapper> getList() {
		return list;
	}

	@Override
	public void mediaItemChanged(Item i) {
		ensureMainThread(true);
		if (i == parent) setParent(parent, false);
	}

	@CallSuper
	@Override
	protected void onItemDismiss(int position) {
		list.remove(position);
		getParent().updateTitles().main().thenRun(this::refresh);
	}

	@CallSuper
	@Override
	protected boolean onItemMove(int fromPosition, int toPosition) {
		activity.getContextMenu().hide();
		move(list, fromPosition, toPosition);
		return true;
	}

	private void move(List<MediaItemWrapper> list, int fromPosition, int toPosition) {
		if (fromPosition < toPosition) {
			for (int i = fromPosition; i < toPosition; i++) {
				swap(list, i, i + 1);
			}
		} else {
			for (int i = fromPosition; i > toPosition; i--) {
				swap(list, i, i - 1);
			}
		}
	}

	private void swap(List<MediaItemWrapper> list, int fromPosition, int toPosition) {
		updatePos(list.get(fromPosition), fromPosition, toPosition);
		updatePos(list.get(toPosition), toPosition, fromPosition);
		Collections.swap(list, fromPosition, toPosition);
	}

	private void updatePos(MediaItemWrapper w, int from, int to) {
		Item i = w.getItem();
		if (i instanceof ItemBase) {
			ItemBase ib = (ItemBase) i;
			if (ib.getSeqNum() == from + 1) ib.setSeqNum(to + 1);
		}
		i.updateTitles().main().onSuccess(t -> activity.post(() -> {
			MediaItemView v = w.getView();
			if (v != null) v.refresh();
		}));
	}

	@NonNull
	@Override
	public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		MediaItemView v = (MediaItemView) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.media_item_view, parent, false);
		v.setClickable(true);
		v.setOnClickListener(this);
		return new MediaItemViewHolder(v, getListView());
	}

	@Override
	public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position) {
		List<MediaItemWrapper> list = getList();
		if (position < list.size()) holder.bind(list.get(position));
	}

	public void onDestroy() {
		for (MediaItemWrapper w : getList()) {
			MediaItemViewHolder h = w.getViewHolder();
			if (h != null) h.recycled();
		}
		setParent(null, false);
	}

	@Override
	public void onViewRecycled(@NonNull MediaItemViewHolder holder) {
		holder.recycled();
	}

	@Override
	public void onViewAttachedToWindow(@NonNull MediaItemViewHolder holder) {
		holder.attached();
	}

	@Override
	public void onViewDetachedFromWindow(@NonNull MediaItemViewHolder holder) {
		holder.detached();
	}

	@Override
	public int getItemCount() {
		return getList().size();
	}

	public boolean isLongPressDragEnabled() {
		return filter == null;
	}

	public boolean isItemViewSwipeEnabled() {
		return false;
	}

	@Override
	public void onClick(View v) {
		MediaItemView mi = (MediaItemView) v;
		Item i = mi.getItem();

		if (i instanceof BrowsableItem) {
			setParent((BrowsableItem) i);
		}
	}

	public boolean hasSelectable() {
		for (MediaItemWrapper w : getList()) {
			if (w.isSelectionSupported()) return true;
		}
		return false;
	}

	public boolean hasSelected() {
		for (MediaItemWrapper w : getList()) {
			if (w.isSelected()) return true;
		}
		return false;
	}

	public List<PlayableItem> getSelectedItems() {
		List<MediaItemWrapper> list = getList();
		List<PlayableItem> selection = new ArrayList<>(list.size());
		for (MediaItemWrapper w : list) {
			if (w.isSelected() && (w.getItem() instanceof PlayableItem))
				selection.add((PlayableItem) w.getItem());
		}
		return selection;
	}

	private void notifyChanged() {
		if ((listView != null) && listView.isComputingLayout())
			App.get().getHandler().post(this::notifyDataSetChanged);
		else notifyDataSetChanged();
	}

	private boolean filter(Item i) {
		if (filter == null) return true;
		MediaDescriptionCompat dsc = i.getMediaDescription().peek();
		CharSequence title;
		if (dsc != null) title = requireNonNull(dsc.getTitle());
		else title = i.getName();
		return filter.matcher(title).find();
	}
}
