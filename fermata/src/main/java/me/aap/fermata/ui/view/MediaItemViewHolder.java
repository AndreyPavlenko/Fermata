package me.aap.fermata.ui.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemViewHolder extends RecyclerView.ViewHolder {
	@NonNull
	private final MediaItemListView listView;
	private boolean attached;
	@Nullable
	private MediaItemWrapper itemWrapper;

	public MediaItemViewHolder(@NonNull MediaItemView itemView, @NonNull MediaItemListView listView) {
		super(itemView);
		this.listView = listView;
		itemView.setHolder(this);
	}

	@NonNull
	public MediaItemView getItemView() {
		return (MediaItemView) itemView;
	}

	@NonNull
	public MediaItemListView getListView() {
		return listView;
	}

	@Nullable
	public MediaItemWrapper getItemWrapper() {
		return itemWrapper;
	}

	public boolean isAttached() {
		return attached;
	}

	void bind(MediaItemWrapper wrapper) {
		MediaItemWrapper old = itemWrapper;
		itemWrapper = wrapper;
		wrapper.setViewHolder(this);
		getItemView().rebind(old, wrapper);
	}

	void attached() {
		attached = true;
		getListView().holderAttached(this);
	}

	void detached() {
		attached = false;
	}

	void recycled() {
		MediaItemWrapper old = itemWrapper;
		itemWrapper = null;
		getItemView().rebind(old, null);
	}
}
