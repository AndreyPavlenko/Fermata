package me.aap.fermata.media.lib;

import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.concurrent.CompletableFuture;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.JointConsumer;

import static me.aap.utils.collection.NaturalOrderComparator.compareNatural;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;
import static me.aap.utils.concurrent.PriorityThreadPool.MAX_PRIORITY;
import static me.aap.utils.concurrent.PriorityThreadPool.MIN_PRIORITY;

/**
 * @author Andrey Pavlenko
 */
abstract class BrowsableItemBase<C extends Item> extends ItemBase implements BrowsableItem,
		BrowsableItemPrefs {
	private volatile List<C> children;
	private volatile Iterator<PlayableItem> shuffle;
	private LoadChildren loadChildren;

	public BrowsableItemBase(String id, @Nullable BrowsableItem parent, @Nullable MediaFile file) {
		super(id, parent, file);
	}

	protected abstract List<C> listChildren();

	protected String getChildrenIdPattern() {
		return null;
	}

	protected Uri getIconUri() {
		return null;
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
	public void getChildren(@Nullable Consumer<List<? extends Item>> listConsumer,
													@Nullable Consumer<List<? extends Item>> sortedListConsumer) {
		List<C> children = this.children;

		if (children != null) {
			consumeInMainThread(listConsumer, children);
			consumeInMainThread(sortedListConsumer, children);
			return;
		}

		LoadChildren load;
		boolean main = isMainThread();
		boolean canBlock = ((listConsumer != null) && listConsumer.canBlockThread()) ||
				((sortedListConsumer != null) && sortedListConsumer.canBlockThread());
		FermataApplication app = FermataApplication.get();
		JointConsumer<List<? extends Item>> consumer = new JointConsumer<>(listConsumer, sortedListConsumer);

		synchronized (this) {
			if (loadChildren == null) loadChildren = load = new LoadChildren();
			else load = loadChildren;
		}

		load.list.addConsumer(consumer.getFirst(), Collections::emptyList, app.getHandler());
		load.sort.addConsumer(consumer, Collections::emptyList, app.getHandler());

		if (sortedListConsumer != null) {
			load.list.addConsumer(list -> {
				if (canBlock || !isMainThread()) {
					load.sort.run(() -> sort(load, list));
				} else {
					byte pri = main ? MAX_PRIORITY : MIN_PRIORITY;
					app.getExecutor().submit(pri, () -> load.sort.run(() -> sort(load, list)));
				}
			}, Collections::emptyList);
		}

		if (canBlock || !main) {
			load.list.run(this::list);
		} else {
			byte pri = main ? MAX_PRIORITY : MIN_PRIORITY;
			app.getExecutor().submit(pri, () -> load.list.run(this::list));
		}
	}

	private List<C> list() {
		List<C> list = Collections.unmodifiableList(listChildren());
		queryMetadata(list);
		return list;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private List sort(LoadChildren load, List list) {
		if (load.sort.isDone()) return load.sort.get(Collections::emptyList);

		list = new ArrayList(list);
		sortChildren(list);
		List children = Collections.unmodifiableList(list);

		if (load.sort.complete(children)) {
			synchronized (this) {
				if (loadChildren == load) {
					shuffle = null;
					this.children = children;
					loadChildren = null;
				}
			}

			return children;
		} else {
			return load.sort.get(Collections::emptyList);
		}
	}

	private void queryMetadata(List<C> children) {
		String pattern = getChildrenIdPattern();
		if (pattern == null) return;

		Map<String, MediaMetadataCompat.Builder> meta = getLib().getMetadataRetriever().queryMetadata(pattern);
		if (meta.isEmpty()) return;

		for (C c : children) {
			if (!(c instanceof PlayableItemBase)) continue;
			MediaMetadataCompat.Builder b = meta.get(c.getId());
			if (b == null) continue;

			PlayableItemBase p = (PlayableItemBase) c;
			p.setParentData(b);
			p.setMediaData(b.build());
		}
	}

	@Override
	void buildCompleteDescription(MediaDescriptionCompat.Builder b) {
		super.buildCompleteDescription(b);
		b.setIconUri(getIconUri());
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

	void setChildren(List<C> children) {
		shuffle = null;
		sortChildren(children);
		this.children = Collections.unmodifiableList(children);
	}

	boolean isChildrenLoaded() {
		return children != null;
	}

	@Override
	public void clearCache() {
		super.clearCache();
		List<C> children = this.children;

		if (children != null) {
			shuffle = null;
			this.children = null;

			for (Item i : children) {
				i.clearCache();
			}
		}
	}

	@Override
	public void updateTitles() {
		super.updateTitles();
		List<C> children = this.children;

		if (children != null) {
			for (Item i : children) {
				i.updateTitles();
			}
		}
	}

	@Override
	public void updateSorting() {
		List<C> children = this.children;
		if (children == null) return;

		for (C c : children) {
			c.updateTitles();
			if (c instanceof BrowsableItem) ((BrowsableItem) c).updateSorting();
		}

		setChildren(new ArrayList<>(children));
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
		final CompletableFuture<List<? extends Item>> sort = new CompletableFuture<>();
	}
}
