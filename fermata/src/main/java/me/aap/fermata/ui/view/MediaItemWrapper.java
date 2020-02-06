package me.aap.fermata.ui.view;

import androidx.annotation.NonNull;

import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemWrapper {
	private final Item item;
	private boolean selected;
	private MediaItemView view;

	public MediaItemWrapper(Item item) {
		this.item = item;
	}

	public Item getItem() {
		return item;
	}

	public void setView(MediaItemView view) {
		this.view = view;
		view.getCheckBox().setSelected(isSelected());
	}

	public MediaItemView getView() {
		return view;
	}

	public void refreshViewCheckbox() {
		if (view != null) view.refreshCheckbox();
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected, boolean refreshViewCheckbox) {
		if (isSelectionSupported()) {
			this.selected = selected;
			if (refreshViewCheckbox) refreshViewCheckbox();
		}
	}

	public boolean isSelectionSupported() {
		return (getItem() instanceof PlayableItem);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof MediaItemWrapper)) return false;
		return getItem().equals(((MediaItemWrapper) o).getItem());
	}

	@Override
	public int hashCode() {
		return Objects.hash(item);
	}

	@NonNull
	@Override
	public String toString() {
		return getItem().toString();
	}
}
