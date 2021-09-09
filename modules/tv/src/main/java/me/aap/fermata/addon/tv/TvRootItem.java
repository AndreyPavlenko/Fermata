package me.aap.fermata.addon.tv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.m3u.TvM3uEpgItem;
import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermata.addon.tv.m3u.TvM3uGroupItem;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.m3u.TvM3uTrackItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.contains;

/**
 * @author Andrey Pavlenko
 */
public class TvRootItem extends ItemContainer<TvM3uItem> implements TvItem {
	public static final String ID = "TV";
	private static final Pref<IntSupplier> SOURCE_COUNTER = Pref.i("SOURCE_COUNTER", 0).withInheritance(false);
	private static final Pref<Supplier<int[]>> SOURCE_IDS = Pref.ia("SOURCE_IDS", () -> new int[0]).withInheritance(false);
	private final DefaultMediaLib lib;

	public TvRootItem(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;

		switch (scheme) {
			case TvM3uItem.SCHEME:
				return create(toSourceId(id));
			case TvM3uGroupItem.SCHEME:
				return TvM3uGroupItem.create(this, id);
			case TvM3uTrackItem.SCHEME:
				return TvM3uTrackItem.create(this, id);
			case TvM3uEpgItem.SCHEME:
				return TvM3uEpgItem.create(this, id);
			default:
				return null;
		}
	}


	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_tv));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public MediaLib.BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public MediaLib.BrowsableItem getRoot() {
		return this;
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		int[] ids = getIntArrayPref(SOURCE_IDS);
		List<Integer> idList = new ArrayList<>(ids.length);
		for (int i : ids) idList.add(i);
		List<Item> children = new ArrayList<>(ids.length);
		return forEach(id -> {
			FutureSupplier<TvM3uItem> f = create(id);
			if (f != null) return f.onSuccess(i -> {
				if (i != null) children.add(i);
			});
			return completedVoid();
		}, idList).map(v -> children);
	}

	@Override
	protected String getScheme() {
		return TvM3uItem.SCHEME;
	}

	@Override
	protected void saveChildren(List<TvM3uItem> children) {
		applyIntArrayPref(SOURCE_IDS, CollectionUtils.map(children,
				(i, t, a) -> a[i] = toSourceId(t.getId()), int[]::new));
	}

	@Override
	public boolean isChildItemId(String id) {
		return id.startsWith(TvM3uTrackItem.SCHEME)
				|| id.startsWith(TvM3uGroupItem.SCHEME)
				|| id.startsWith(TvM3uItem.SCHEME);
	}

	public void addSource(TvM3uFile m3u) {
		int counter = getIntPref(SOURCE_COUNTER) + 1;
		Pref<Supplier<String>> id = Pref.s("M3UID#" + counter);

		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setIntPref(SOURCE_COUNTER, counter);
			e.setStringPref(id, TvM3uFileSystem.getInstance().toId(m3u.getRid()));
		}

		addItem(TvM3uItem.create(this, m3u, counter));
	}

	@Override
	protected void itemRemoved(TvM3uItem i) {
		super.itemRemoved(i);
		TvM3uFileSystemProvider.removeSource(i.getResource());
	}

	private FutureSupplier<TvM3uItem> create(int srcId) {
		if (!contains(getIntArrayPref(SOURCE_IDS), srcId)) return null;
		String m3uId = getStringPref(Pref.s("M3UID#" + srcId));
		if (m3uId == null) return null;
		return TvM3uItem.create(this, srcId, m3uId).onFailure(err -> {
			Log.e(err, "Failed to load source: ", m3uId);
			if (err instanceof MalformedURLException) removeSource(srcId);
		}).ifNull(() -> {
			Log.e("Failed to load source: ", m3uId);
			removeSource(srcId);
			return null;
		});
	}

	private void removeSource(int srcId) {
		int[] ids = getIntArrayPref(SOURCE_IDS);
		if (ids.length == 0) return;

		int[] newIds = new int[ids.length - 1];
		boolean removed = false;

		for (int i = 0, j = 0; i < ids.length; i++) {
			if (ids[i] == srcId) removed = true;
			else if (j < newIds.length) newIds[j++] = ids[i];
			else return;
		}

		if (removed) {
			Log.i("Removing source: ", srcId);
			applyIntArrayPref(SOURCE_IDS, newIds);
		}
	}

	private int toSourceId(String id) {
		return Integer.parseInt(id.substring(6));
	}
}
