package me.aap.fermata.ui.view;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemViewHolder extends RecyclerView.ViewHolder {

	public MediaItemViewHolder(@NonNull MediaItemView itemView) {
		super(itemView);
	}

	public MediaItemView getItemView() {
		return (MediaItemView) itemView;
	}
}
