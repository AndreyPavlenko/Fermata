package me.aap.fermata.ui.view;

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
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.log.Log;
import me.aap.utils.ui.view.MovableRecyclerViewAdapter;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.filterMap;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemListViewAdapter extends MovableRecyclerViewAdapter<MediaItemViewHolder>
		implements OnClickListener {
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

	public FutureSupplier<?> setParent(BrowsableItem parent) {
		return setParent(parent, true);
	}

	@CallSuper
	public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
		this.parent = parent;
		list = Collections.emptyList();
		notifyDataSetChanged();
		if (parent == null) return completedVoid();

		FutureSupplier<?> f = parent.getChildren().main()
				.addConsumer((result, fail, progress, total) -> {
					if (this.parent != parent) return;

					if (fail != null) {
						if (isCancellation(fail)) return;
						Log.e(fail, "Failed to load children");
						Utils.showAlert(activity.getContext(), fail.getLocalizedMessage());
					} else {
						setChildren(result);
					}
				});

		if (userAction) activity.setContentLoading(f);
		return f;
	}

	@CallSuper
	protected void setChildren(List<? extends Item> children) {
		list = filterMap(children, this::filter, (i, c, l) -> l.add(new MediaItemWrapper(c)), ArrayList::new);
		notifyDataSetChanged();
	}

	public void setFilter(String filter) {
		if (!filter.equals(filterText)) {
			filterText = filter;
			this.filter = filter.isEmpty() ? null : Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE);
			setParent(getParent());
		}
	}

	public void reload() {
		getListView().discardSelection();
		setParent(getParent());
	}

	public void refresh() {
		getListView().refresh();
	}

	public List<MediaItemWrapper> getList() {
		return list;
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
		MediaItemListView listView = getListView();
		View c = listView.getChildAt(fromPosition);
		if (c == null) return false;
		MediaItemViewHolder h = (MediaItemViewHolder) listView.getChildViewHolder(c);
		h.getItemView().hideMenu();
		CollectionUtils.move(list, fromPosition, toPosition);
		getParent().updateTitles().main().thenRun(this::refresh);
		return true;
	}

	@NonNull
	@Override
	public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		MediaItemView v = (MediaItemView) LayoutInflater.from(parent.getContext()).inflate(R.layout.media_item_view, parent, false);
		v.setClickable(true);
		v.setOnClickListener(this);
		v.setListView(getListView());
		return new MediaItemViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position) {
		List<MediaItemWrapper> list = getList();
		if (position < list.size()) holder.getItemView().setItemWrapper(list.get(position));
	}

	@Override
	public void onViewRecycled(@NonNull MediaItemViewHolder holder) {
		MediaItemView i = holder.getItemView();
		if (i != null) i.cancelLoading();
	}

	@Override
	public int getItemCount() {
		return getList().size();
	}

	public boolean isLongPressDragEnabled() {
		return filter == null;
	}

	public boolean isItemViewSwipeEnabled() {
		return filter == null;
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

	private boolean filter(Item i) {
		if (filter == null) return true;
		MediaDescriptionCompat dsc = i.getMediaDescription().peek();
		CharSequence title;

		if (dsc != null) {
			title = requireNonNull(dsc.getTitle());
		} else if (i instanceof BrowsableItem) {
			title = ((BrowsableItem) i).getName();
		} else {
			title = i.getResource().getName();
		}

		return filter.matcher(title).find();
	}
}
