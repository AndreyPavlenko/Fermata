package me.aap.fermata.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemListView extends RecyclerView {
	private boolean isSelectionActive;

	public MediaItemListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@NonNull
	@Override
	public MediaItemListViewAdapter getAdapter() {
		return (MediaItemListViewAdapter) requireNonNull(super.getAdapter());
	}

	public boolean isSelectionActive() {
		return isSelectionActive;
	}

	public void select(boolean select) {
		if (!select && !isSelectionActive) return;

		boolean selectAll = isSelectionActive;
		isSelectionActive = select;

		for (MediaItemWrapper w : getAdapter().getList()) {
			if (selectAll) w.setSelected(select, true);
			else w.refreshViewCheckbox();
		}
	}

	public void discardSelection() {
		select(false);
	}

	public void refresh() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refresh();
		}
	}

	public void refreshState() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refreshState();
		}
	}
}
