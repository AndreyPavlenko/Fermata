package me.aap.fermata.media.lib;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completedEmptyList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;


/**
 * @author Andrey Pavlenko
 */
public abstract class ItemContainer<C extends Item> extends BrowsableItemBase {

	protected ItemContainer(String id, @Nullable BrowsableItem parent, @Nullable VirtualResource file) {
		super(id, parent, file);
	}

	protected abstract String getScheme();

	protected abstract void saveChildren(List<C> children);

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	FutureSupplier<Item> getItem(String id) {
		assert id.startsWith(getScheme());

		return list().map(list -> {
			for (C i : list) if (id.equals(i.getId())) return i;
			return null;
		});
	}

	FutureSupplier<List<Item>> listChildren(PreferenceStore prefs, Pref<Supplier<String[]>> idsPref) {
		String[] ids = prefs.getStringArrayPref(idsPref);
		if ((ids == null) || (ids.length == 0)) return completedEmptyList();
		MediaLib lib = getLib();
		List<Item> children = new ArrayList<>(ids.length);
		AtomicBoolean update = new AtomicBoolean();
		AtomicInteger counter = new AtomicInteger();
		return forEach(id -> lib.getItem(id)
				.ifFail(err -> {
					Log.e(err, "Failed to get item: ", id);
					counter.incrementAndGet();
					return null;
				}).map(c -> {
					if (c == null) {
						Log.w("Item not found: ", ids[counter.get()]);
					} else {
						children.add(toChildItem(c));
						String newId = c.getId();
						String oldId = ids[counter.get()];
						if (!newId.equals(oldId)) {
							Log.i("Item id has been changed. Updating ", oldId, " -> ", newId);
							ids[counter.get()] = newId;
							update.set(true);
						}
					}

					counter.incrementAndGet();
					return null;
				}), ids).main().map(v -> {
			if (update.get()) prefs.applyStringArrayPref(idsPref, ids);
			return children;
		});
	}

	public FutureSupplier<Void> addItem(C item) {
		return list().map(children -> {
			C i = toChildItem(item);
			if (children.contains(i)) return null;

			List<C> newChildren = new ArrayList<>(children.size() + 1);
			newChildren.addAll(children);
			newChildren.add(i);
			itemAdded(i);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	public FutureSupplier<Void> addItems(List<C> items) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list.size() + items.size());
			boolean added = false;
			newChildren.addAll(list);

			for (C i : items) {
				i = toChildItem(i);
				if (list.contains(i)) continue;
				newChildren.add(i);
				itemAdded(i);
				added = true;
			}

			if (!added) return null;

			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	public FutureSupplier<Void> removeItem(int idx) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			C removed = newChildren.remove(idx);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			itemRemoved(removed);
			return null;
		});
	}

	public FutureSupplier<Void> removeItem(C item) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			C i = toChildItem(item);
			if (!newChildren.remove(i)) return null;

			setNewChildren(newChildren);
			saveChildren(newChildren);
			itemRemoved(i);
			return null;
		});
	}

	public FutureSupplier<Void> removeItems(List<C> items) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			List<C> removed = new ArrayList<>(items.size());

			for (C i : items) {
				if (newChildren.remove(i = toChildItem(i))) removed.add(i);
			}

			if (removed.isEmpty()) return null;
			setNewChildren(newChildren);
			saveChildren(newChildren);
			CollectionUtils.forEach(removed, this::itemRemoved);
			return null;
		});
	}

	protected void itemAdded(C i) {
	}

	@CallSuper
	protected void itemRemoved(C i) {
		getLib().removeFromCache(i);
	}

	public FutureSupplier<Void> moveItem(int fromPosition, int toPosition) {
		return list().map(list -> {
			List<C> newChildren = new ArrayList<>(list);
			CollectionUtils.move(newChildren, fromPosition, toPosition);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
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
	private C toChildItem(Item i) {
		String id = i.getId();
		if (isChildItemId(id)) return (C) i;
		if (!(i instanceof PlayableItem)) throw new IllegalArgumentException("Unsupported child: " + i);

		PlayableItem pi = (PlayableItem) i;
		return (C) pi.export(toChildItemId(pi.getOrigId()), this);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected FutureSupplier<List<C>> list() {
		return (FutureSupplier) getUnsortedChildren().main();
	}
}
