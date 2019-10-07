package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.storage.MediaFile;

import static me.aap.fermata.util.NaturalOrderComparator.compareNatural;

/**
 * @author Andrey Pavlenko
 */
abstract class BrowsableItemBase<C extends Item> extends ItemBase implements BrowsableItem,
		BrowsableItemPrefs {
	private List<C> children;
	private List<C> unsortedChildren;
	private Iterator<PlayableItem> shuffle;

	public BrowsableItemBase(String id, @Nullable BrowsableItem parent, @Nullable MediaFile file) {
		super(id, parent, file);
	}

	protected abstract List<C> listChildren();

	@Override
	public BrowsableItemPrefs getPrefs() {
		return this;
	}

	public List<C> getChildren() {
		if (children == null) {
			if (unsortedChildren != null) {
				setChildren(unsortedChildren);
			} else {
				setChildren(listChildren());
			}
		}
		return children;
	}

	@Override
	public List<C> getChildren(@Nullable Consumer<List<? extends Item>> onLoadingCompletion) {
		if (this.children != null) {
			if (onLoadingCompletion != null) onLoadingCompletion.accept(this.children);
			return this.children;
		}

		if (unsortedChildren == null) unsortedChildren = listChildren();

		if (onLoadingCompletion == null) return Collections.unmodifiableList(unsortedChildren);

		List<C> children = unsortedChildren;

		for (Iterator<C> it = new ArrayList<>(children).iterator(); it.hasNext(); ) {
			C c = it.next();

			if (c instanceof PlayableItem) {
				PlayableItem pi = (PlayableItem) c;

				if (!pi.isMediaDataLoaded()) {
					pi.getMediaData(m -> loadChildren(children, it, onLoadingCompletion));
					return Collections.unmodifiableList(children);
				}
			}
		}

		setChildren(children);
		onLoadingCompletion.accept(this.children);
		return this.children;
	}

	@Override
	public Iterator<PlayableItem> getShuffleIterator() {
		if ((shuffle == null) || !shuffle.hasNext()) {
			Random rnd = ThreadLocalRandom.current();
			List<C> children = new ArrayList<>(getChildren(null));
			List<PlayableItem> list = new ArrayList<>(children.size());

			for (int size = children.size(); size > 0; size = children.size()) {
				C c = children.remove(rnd.nextInt(size));
				if (c instanceof PlayableItem) list.add((PlayableItem) c);
			}

			shuffle = list.isEmpty() ? Collections.emptyIterator() : list.iterator();
		}

		return shuffle;
	}

	void setChildren(List<C> children) {
		sortChildren(children);
		shuffle = null;
		unsortedChildren = null;
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
		unsortedChildren = null;
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
		if (children != null) {
			if (getPrefs().getSortByPref() == BrowsableItemPrefs.SORT_BY_NONE) {
				shuffle = null;
				children = null;
				unsortedChildren = null;
			} else {
				for (C c : children) {
					c.updateTitles();
					if (c instanceof BrowsableItem) ((BrowsableItem) c).updateSorting();
				}

				setChildren(new ArrayList<>(children));
			}
		}
	}

	private void loadChildren(List<C> children, Iterator<C> it,
														Consumer<List<? extends Item>> onLoadingCompletion) {
		while (it.hasNext()) {
			C c = it.next();

			if (c instanceof PlayableItem) {
				PlayableItem pi = (PlayableItem) c;

				if (!pi.isMediaDataLoaded()) {
					pi.getMediaData(m -> loadChildren(children, it, onLoadingCompletion));
					return;
				}
			}
		}

		if (unsortedChildren == children) setChildren(children);
		onLoadingCompletion.accept(this.children != null ? this.children :
				Collections.unmodifiableList(children));
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
}
