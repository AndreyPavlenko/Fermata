package me.aap.utils.ui.view;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.util.Collections;
import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public abstract class MovableRecyclerViewAdapter<VH extends ViewHolder>
		extends RecyclerView.Adapter<VH> {
	private boolean isCallbackCall;

	protected abstract void onItemDismiss(int position);

	protected abstract boolean onItemMove(int fromPosition, int toPosition);

	protected boolean isLongPressDragEnabled() {
		return true;
	}

	protected boolean isItemViewSwipeEnabled() {
		return true;
	}

	public boolean isCallbackCall() {
		return isCallbackCall;
	}

	public ItemTouchHelper.Callback getItemTouchCallback() {
		return new ItemTouchHelper.Callback() {

			@Override
			public boolean isLongPressDragEnabled() {
				return MovableRecyclerViewAdapter.this.isLongPressDragEnabled();
			}

			@Override
			public boolean isItemViewSwipeEnabled() {
				return MovableRecyclerViewAdapter.this.isItemViewSwipeEnabled();
			}

			@Override
			public int getMovementFlags(@NonNull RecyclerView recyclerView,
																	@NonNull ViewHolder viewHolder) {
				int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT |
						ItemTouchHelper.RIGHT;
				int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
				return makeMovementFlags(dragFlags, swipeFlags);
			}

			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder,
														@NonNull ViewHolder target) {
				int from = viewHolder.getAdapterPosition();
				int to = target.getAdapterPosition();
				isCallbackCall = true;

				if (MovableRecyclerViewAdapter.this.onItemMove(from, to)) {
					notifyItemMoved(from, to);
					isCallbackCall = false;
					return true;
				} else {
					isCallbackCall = false;
					return false;
				}
			}

			@Override
			public void onSwiped(@NonNull ViewHolder viewHolder, int direction) {
				int pos = viewHolder.getAdapterPosition();
				isCallbackCall = true;
				MovableRecyclerViewAdapter.this.onItemDismiss(pos);
				isCallbackCall = false;
				notifyItemRemoved(pos);
			}
		};
	}

	protected void move(List<?> list, int fromPosition, int toPosition) {
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

	protected void swap(List<?> list, int fromPosition, int toPosition) {
		Collections.swap(list, fromPosition, toPosition);
	}
}
