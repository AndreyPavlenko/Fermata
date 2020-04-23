package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.text.SharedTextBuilder;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;


/**
 * @author Andrey Pavlenko
 */
abstract class ItemContainer<C extends Item> extends BrowsableItemBase {

	ItemContainer(String id, @Nullable BrowsableItem parent, @Nullable VirtualResource file) {
		super(id, parent, file);
	}

	abstract String getScheme();

	abstract void saveChildren(List<C> children);

	FutureSupplier<Item> getItem(String id) {
		assert id.startsWith(getScheme());

		for (C i : list()) {
			if (id.equals(i.getId())) return completed(i);
		}

		return completedNull();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	FutureSupplier<List<Item>> listChildren(String[] ids) {
		MediaLib lib = getLib();
		List children = new ArrayList<>(ids.length);
		return forEach(id -> lib.getItem(id).map(children::add), ids).then(v -> completed(children));
	}

	public void addItem(C i) {
		List<C> children = list();
		i = toChildItem(i);
		if (children.contains(i)) return;

		List<C> newChildren = new ArrayList<>(children.size() + 1);
		newChildren.addAll(children);
		newChildren.add(i);
		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	public void addItems(List<C> items) {
		List<C> children = list();
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

		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	public void removeItem(int idx) {
		List<C> newChildren = new ArrayList<>(list());
		newChildren.remove(idx);
		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	public void removeItem(C i) {
		List<C> newChildren = new ArrayList<>(list());
		if (!newChildren.remove(toChildItem(i))) return;

		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	public void removeItems(List<C> items) {
		List<C> newChildren = new ArrayList<>(list());
		boolean removed = false;

		for (C i : items) {
			if (newChildren.remove(toChildItem(i))) removed = true;
		}

		if (!removed) return;

		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	public void moveItem(int fromPosition, int toPosition) {
		List<C> newChildren = new ArrayList<>(list());
		CollectionUtils.move(newChildren, fromPosition, toPosition);
		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	public boolean isChildItemId(String id) {
		return id.startsWith(getScheme());
	}

	public String toChildItemId(String id) {
		if (isChildItemId(id)) return id;
		SharedTextBuilder tb = SharedTextBuilder.get();
		return tb.append(getScheme()).append(':').append(id).releaseString();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void setNewChildren(List<C> c) {
		super.setChildren((List) c);
	}

	@SuppressWarnings("unchecked")
	public C toChildItem(C i) {
		String id = i.getId();
		if (isChildItemId(id)) return i;
		if (!(i instanceof PlayableItem)) throw new IllegalArgumentException("Unsupported child: " + i);

		PlayableItem pi = (PlayableItem) i;
		return (C) pi.export(toChildItemId(pi.getOrigId()), this);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private List<C> list() {
		return (List) getUnsortedChildren().getOrThrow();
	}
}
