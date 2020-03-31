package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.text.TextUtils;


/**
 * @author Andrey Pavlenko
 */
abstract class ItemContainer<C extends Item> extends BrowsableItemBase<C> {

	ItemContainer(String id, @Nullable BrowsableItem parent, @Nullable MediaFile file) {
		super(id, parent, file);
	}

	abstract String getScheme();

	abstract void saveChildren(List<C> children);

	Item getItem(String id) {
		assert id.startsWith(getScheme());

		for (C i : getUnsortedChildren()) {
			if (id.equals(i.getId())) return i;
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	List<C> listChildren(String[] ids) {
		MediaLib lib = getLib();
		List<C> children = new ArrayList<>(ids.length);

		for (String id : ids) {
			Item i = lib.getItem(id);
			if (i != null) children.add(toChildItem((C) i));
		}

		return children;
	}

	public void addItem(C i) {
		List<C> children = getUnsortedChildren();
		i = toChildItem(i);
		if (children.contains(i)) return;

		List<C> newChildren = new ArrayList<>(children.size() + 1);
		newChildren.addAll(children);
		newChildren.add(i);
		setChildren(newChildren);
		saveChildren(newChildren);
	}

	public void addItems(List<C> items) {
		List<C> children = getUnsortedChildren();
		List<C> newChildren = new ArrayList<>(children.size() + items.size());
		boolean added = false;
		newChildren.addAll(children);

		for (C i : items) {
			i = toChildItem(i);
			if (children.contains(i)) continue;
			newChildren.add(i);
			added = true;
		}

		if (!added) return;

		setChildren(newChildren);
		saveChildren(newChildren);
	}

	public void removeItem(int idx) {
		List<C> newChildren = new ArrayList<>(getUnsortedChildren());
		newChildren.remove(idx);
		setChildren(newChildren);
		saveChildren(newChildren);
	}

	public void removeItem(C i) {
		List<C> newChildren = new ArrayList<>(getUnsortedChildren());
		if (!newChildren.remove(toChildItem(i))) return;

		setChildren(newChildren);
		saveChildren(newChildren);
	}

	public void removeItems(List<C> items) {
		List<C> newChildren = new ArrayList<>(getUnsortedChildren());
		boolean removed = false;

		for (C i : items) {
			if (newChildren.remove(toChildItem(i))) removed = true;
		}

		if (!removed) return;

		setChildren(newChildren);
		saveChildren(newChildren);
	}

	public void moveItem(int fromPosition, int toPosition) {
		List<C> newChildren = new ArrayList<>(getUnsortedChildren());
		CollectionUtils.move(newChildren, fromPosition, toPosition);
		setChildren(newChildren);
		saveChildren(newChildren);
	}

	public void updateSorting() {
		super.updateSorting();
		saveChildren(getUnsortedChildren());
	}

	public boolean isChildItemId(String id) {
		return id.startsWith(getScheme());
	}

	public String toChildItemId(String id) {
		if (isChildItemId(id)) return id;
		StringBuilder sb = TextUtils.getSharedStringBuilder();
		return sb.append(getScheme()).append(':').append(id).toString();
	}

	@SuppressWarnings("unchecked")
	public C toChildItem(C i) {
		String id = i.getId();
		if (isChildItemId(id)) return i;
		if (!(i instanceof PlayableItem)) throw new IllegalArgumentException("Unsupported child: " + i);

		PlayableItem pi = (PlayableItem) i;
		return (C) pi.export(toChildItemId(pi.getOrigId()), this);
	}
}
