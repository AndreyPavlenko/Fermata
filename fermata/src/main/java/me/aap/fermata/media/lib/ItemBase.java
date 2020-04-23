package me.aap.fermata.media.lib;

import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.vfs.VirtualResource;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.misc.Assert.assertNotEquals;

/**
 * @author Andrey Pavlenko
 */
abstract class ItemBase implements Item, MediaPrefs, SharedPreferenceStore {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final AtomicReferenceFieldUpdater<ItemBase, FutureSupplier<MediaDescriptionCompat>> description =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(ItemBase.class, FutureSupplier.class, "descriptionHolder");
	@Keep
	@SuppressWarnings("unused")
	private volatile FutureSupplier<MediaDescriptionCompat> descriptionHolder;
	private final String id;
	private final BrowsableItem parent;
	private final VirtualResource file;
	protected int seqNum;

	public ItemBase(String id, @Nullable BrowsableItem parent, VirtualResource file) {
		this.id = id.intern();
		this.parent = parent;
		this.file = file;
		DefaultMediaLib lib = getLib();
		//noinspection ConstantConditions
		if (lib != null) lib.addToCache(this);
	}

	protected abstract FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs);

	protected abstract FutureSupplier<String> buildSubtitle();

	@NonNull
	@Override
	public String getId() {
		return id;
	}

	@NonNull
	@Override
	public FutureSupplier<MediaDescriptionCompat> getMediaDescription() {
		FutureSupplier<MediaDescriptionCompat> d = description.get(this);
		if (d != null) return d;

		Promise<MediaDescriptionCompat> load = getLib().newPromise();

		for (; !description.compareAndSet(this, null, load); d = description.get(this)) {
			if (d != null) return d;
		}

		MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
		b.setMediaId(getId());

		FutureSupplier<String> title = buildTitle();
		FutureSupplier<String> subtitle = buildSubtitle();
		FutureSupplier<Uri> icon = getIconUri();

		if (title.isDone() && subtitle.isDone() && icon.isDone()) {
			b.setTitle(title.get(getFile()::getName));
			b.setSubtitle(subtitle.get(() -> ""));
			b.setIconUri(icon.get(null));
			MediaDescriptionCompat dsc = b.build();
			load.complete(dsc);
			d = completed(dsc);
			return description.compareAndSet(this, load, d) ? d : getMediaDescription();
		}

		title.then(t -> {
			b.setTitle(t);
			load.setProgress(b.build(), 1, 3);
			return subtitle.then(s -> {
				b.setSubtitle(s);
				load.setProgress(b.build(), 2, 3);
				return icon.then(u -> {
					if (u != null) b.setIconUri(u);
					return completed(b.build());
				});
			});
		}).thenReplaceOrClear(description, this, load);

		return description.get(this);
	}

	void setMediaDescription(MediaDescriptionCompat dsc) {
		setMediaDescription(completed(dsc));
	}

	void setMediaDescription(FutureSupplier<MediaDescriptionCompat> dsc) {
		description.set(this, dsc);
		dsc.thenReplaceOrClear(description, this);
	}

	protected FutureSupplier<String> buildTitle() {
		BrowsableItem parent = requireNonNull(getParent());
		BrowsableItemPrefs prefs = parent.getPrefs();
		int seq = seqNum;

		if (prefs.getTitleSeqNumPref()) {
			if (seq == 0) {
				return parent.getChildren().then(children -> {
					int s = seqNum;
					assertNotEquals(s, 0);
					return buildTitle(s, prefs);
				});
			} else {
				return buildTitle(seq, prefs);
			}
		} else {
			return buildTitle(0, prefs);
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Void> updateTitles() {
		description.set(this, null);
		return completedVoid();
	}

	@Override
	public BrowsableItem getParent() {
		return parent;
	}

	@Override
	public VirtualResource getFile() {
		return file;
	}

	@NonNull
	@Override
	public BrowsableItem getRoot() {
		return requireNonNull(getParent()).getRoot();
	}

	@NonNull
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) getRoot().getLib();
	}

	@NonNull
	@Override
	public MediaPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return getLib().getSharedPreferences();
	}

	@Nullable
	@Override
	public PreferenceStore getParentPreferenceStore() {
		BrowsableItem p = getParent();
		return (p != null) ? p.getPrefs() : null;
	}

	@NonNull
	@Override
	public PreferenceStore getRootPreferenceStore() {
		return getLib().getPrefs();
	}

	@Override
	public String getPreferenceKey(Pref<?> key) {
		return getId() + "#" + key.getName();
	}

	@Override
	public int hashCode() {
		return getId().hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof Item) {
			return getId().equals(((Item) obj).getId());
		} else {
			return false;
		}
	}

	@NonNull
	@Override
	public String toString() {
		return getId();
	}

	void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}
}
