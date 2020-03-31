package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.app.App;
import me.aap.utils.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static me.aap.utils.collection.NaturalOrderComparator.compareNatural;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;

/**
 * @author Andrey Pavlenko
 */
abstract class BrowsableItemBase<C extends Item> extends ItemBase implements BrowsableItem,
		BrowsableItemPrefs {
	private volatile List<C> children;
	private Iterator<PlayableItem> shuffle;
	private LoadChildren loadChildren;

	public BrowsableItemBase(String id, @Nullable BrowsableItem parent, @Nullable MediaFile file) {
		super(id, parent, file);
	}

	protected abstract List<C> listChildren();

	protected boolean canListInMainThread() {
		return true;
	}

	@Override
	public BrowsableItemPrefs getPrefs() {
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<C> getChildren() {
		List<C> children = this.children;
		return (children != null) ? children : (List<C>) BrowsableItem.super.getChildren();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<C> getUnsortedChildren() {
		List<C> children = this.children;
		if (children != null) return children;

		LoadChildren load;

		synchronized (this) {
			load = this.loadChildren;
		}

		return (load != null) ? (List<C>) load.list.get(Collections::emptyList)
				: (List<C>) BrowsableItem.super.getUnsortedChildren();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void getChildren(@Nullable Consumer<List<? extends Item>> listCallback,
													@Nullable Consumer<List<? extends Item>> completionCallback) {
		List<C> children = this.children;

		if (children != null) {
			consumeInMainThread(listCallback, children);
			consumeInMainThread(completionCallback, children);
			return;
		}

		boolean run;
		LoadChildren load;

		synchronized (this) {
			if (loadChildren == null) {
				run = true;
				loadChildren = load = new LoadChildren();
				if (completionCallback != null) load.sort = new CompletableFuture<>();
			} else {
				load = loadChildren;

				if ((completionCallback != null) && (load.sort == null)) {
					run = true;
					load.sort = new CompletableFuture<>();
				} else {
					run = false;
				}
			}
		}

		load.list.addConsumer(listCallback, Collections::emptyList, App.get().getHandler());

		if (load.sort != null) {
			load.sort.addConsumer(completionCallback, Collections::emptyList, App.get().getHandler());
			if (load.sort.isDone()) return;
		} else if (load.list.isDone()) {
			return;
		}

		boolean main = isMainThread();
		List<C> list;

		if (load.list.isDone()) {
			list = new ArrayList<>((List<C>) load.list.get(null));
		} else if (!main) {
			list = listChildren();
			load.list.complete(new ArrayList<>(list));
			if (load.sort == null) return;
		} else if (canListInMainThread()) {
			// Do not list in main thread if there are no Future consumers
			if (!(listCallback instanceof Future) && !(completionCallback instanceof Future)) {
				list = null;
			} else {
				list = listChildren();
				load.list.complete(new ArrayList<>(list));
				if (load.sort == null) return;
			}
		} else {
			list = null;
		}

		if (run || !main) {
			if (main) App.get().getExecutor().submit(() -> loadChildren(load, list));
			else loadChildren(load, list);
		}
	}

	@SuppressWarnings("unchecked")
	private void loadChildren(LoadChildren load, List<C> list) {
		CompletableFuture<List<? extends Item>> sort = load.sort;
		if ((sort != null) && sort.isDone()) return;

		if (list == null) {
			if (load.list.isDone()) {
				list = new ArrayList<>((List<C>) load.list.get(null));
			} else {
				list = listChildren();
				load.list.complete(new ArrayList<>(list));
			}
		}

		if (sort == null) {
			synchronized (this) {
				if ((sort = load.sort) == null) return;
			}
		}

		sortChildren(list);

		if (sort.complete(list)) {
			List<C> children = list;
			App.get().getHandler().post(() -> {
				if (this.children == null) {
					this.children = children;
					shuffle = null;
				}
				synchronized (this) {
					if (this.loadChildren == load) loadChildren = null;
				}
			});
		}
	}

	@Override
	public Iterator<PlayableItem> getShuffleIterator() {
		if ((shuffle == null) || !shuffle.hasNext()) {
			Random rnd = ThreadLocalRandom.current();
			List<? extends Item> children = new ArrayList<>(getUnsortedChildren());
			List<PlayableItem> list = new ArrayList<>(children.size());

			for (int size = children.size(); size > 0; size = children.size()) {
				Item c = children.remove(rnd.nextInt(size));
				if (c instanceof PlayableItem) list.add((PlayableItem) c);
			}

			shuffle = list.isEmpty() ? Collections.emptyIterator() : list.iterator();
		}

		return shuffle;
	}

	void setChildren(List<C> children, boolean sort) {
		shuffle = null;
		if (sort) sortChildren(children);
		this.children = Collections.unmodifiableList(children);
	}

	@Override
	public void clearCache() {
		super.clearCache();

		if (children != null) {
			for (Item i : children) {
				i.clearCache();
			}
		}

		shuffle = null;
		children = null;
	}

	@Override
	public void updateTitles() {
		super.updateTitles();

		if (children != null) {
			for (Item i : children) {
				i.updateTitles();
			}
		}
	}

	@Override
	public void updateSorting() {
		if (children == null) return;

		for (C c : children) {
			c.updateTitles();
			if (c instanceof BrowsableItem) ((BrowsableItem) c).updateSorting();
		}

		setChildren(new ArrayList<>(children), true);
	}

	private void sortChildren(List<C> children) {
		switch (getPrefs().getSortByPref()) {
			case BrowsableItemPrefs.SORT_BY_FILE_NAME:
				Collections.sort(children, this::compareByFile);
				break;
			case BrowsableItemPrefs.SORT_BY_NAME:
				Collections.sort(children, this::compareByName);
				break;
		}

		for (int i = 0; i < children.size(); i++) {
			((ItemBase) children.get(i)).setSeqNum(i + 1);
		}
	}


	int compareByFile(Item i1, Item i2) {
		if (i1 instanceof BrowsableItem) {
			if (i2 instanceof BrowsableItem) {
				MediaFile f1 = i1.getFile();
				MediaFile f2 = i2.getFile();
				return (f1 != null) && (f2 != null) ? compareNatural(f1.getName(), f2.getName()) :
						compareNatural(i1.getName(), i2.getName());
			} else {
				return -1;
			}
		} else if (i2 instanceof BrowsableItem) {
			return 1;
		} else {
			MediaFile f1 = i1.getFile();
			MediaFile f2 = i2.getFile();
			return (f1 != null) && (f2 != null) ? compareNatural(f1.getName(), f2.getName()) :
					compareNatural(i1.getName(), i2.getName());
		}
	}

	int compareByName(Item i1, Item i2) {
		if (i1 instanceof BrowsableItem) {
			return (i2 instanceof BrowsableItem) ? compareNatural(i1.getName(), i2.getName()) : -1;
		} else if (i2 instanceof BrowsableItem) {
			return 1;
		} else {
			return compareNatural(i1.getName(), i2.getName());
		}
	}

	private static final class LoadChildren {
		final CompletableFuture<List<? extends Item>> list = new CompletableFuture<>();
		@Nullable
		CompletableFuture<List<? extends Item>> sort;
	}
}
