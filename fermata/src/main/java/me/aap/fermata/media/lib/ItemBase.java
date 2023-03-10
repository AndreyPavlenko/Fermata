package me.aap.fermata.media.lib;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.misc.Assert.assertNotEquals;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public abstract class ItemBase implements Item, MediaPrefs, SharedPreferenceStore {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final AtomicReferenceFieldUpdater<ItemBase,
			FutureSupplier<MediaDescriptionCompat>>
			MD =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(ItemBase.class,
					FutureSupplier.class, "description");
	@Keep
	@SuppressWarnings({"unused", "FieldCanBeLocal"})
	private volatile FutureSupplier<MediaDescriptionCompat> description;
	private final String id;
	private final BrowsableItem parent;
	private final VirtualResource file;
	protected int seqNum;

	public ItemBase(String id, @Nullable BrowsableItem parent, VirtualResource resource) {
		this.id = id.intern();
		this.parent = parent;
		this.file = resource;

		if (!isExternal() && (getLib() instanceof DefaultMediaLib)) {
			((DefaultMediaLib) getLib()).addToCache(this);
		}
	}

	protected abstract FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs);

	protected abstract FutureSupplier<String> buildSubtitle();

	protected FutureSupplier<Bundle> buildExtras() {
		return completedNull();
	}

	@NonNull
	@Override
	public String getId() {
		return id;
	}

	@NonNull
	@Override
	public FutureSupplier<MediaDescriptionCompat> getMediaDescription() {
		FutureSupplier<MediaDescriptionCompat> d = MD.get(this);
		if (isMediaDescriptionValid(d)) return d;

		Promise<MediaDescriptionCompat> load = new Promise<>();

		for (; !MD.compareAndSet(this, d, load); d = MD.get(this)) {
			if (d != null) return d.fork();
		}

		FutureSupplier<String> title = buildTitle();
		FutureSupplier<String> subtitle = buildSubtitle();
		FutureSupplier<Bundle> extras = buildExtras();
		FutureSupplier<Uri> icon = getIconUri();

		if (title.isDone() && subtitle.isDone() && icon.isDone() && extras.isDone()) {
			MediaDescriptionCompat.Builder b = builder();
			b.setTitle(title.get(this::getName));
			b.setSubtitle(subtitle.get(() -> ""));
			b.setIconUri(icon.get(null));
			b.setExtras(extras.get(null));
			MediaDescriptionCompat dsc = b.build();
			load.complete(dsc);
			d = completed(dsc);
			return MD.compareAndSet(this, load, d) ? d : getMediaDescription();
		}

		title.onSuccess(t -> load.setProgress(builder().setTitle(t).build(), 1, 4));
		subtitle.onSuccess(s -> load.setProgress(builder().setSubtitle(s).build(), 2, 4));
		extras.onSuccess(e -> load.setProgress(builder().setExtras(e).build(), 3, 4));
		icon.onSuccess(i -> load.setProgress(builder().setIconUri(i).build(), 4, 4));
		Async.all(title, subtitle, extras, icon).onCompletion((e, err) -> {
			if (err != null) Log.d(err, "Failed to build MediaDescription: ", this);
			MediaDescriptionCompat.Builder b = builder();
			b.setTitle(title.peek(this::getName));
			b.setSubtitle(subtitle.peek(() -> ""));
			b.setIconUri(icon.peek(() -> null));
			b.setExtras(extras.peek(() -> null));
			MediaDescriptionCompat md = b.build();
			MD.compareAndSet(this, load, completed(md));
			load.complete(md);
		});

		d = MD.get(this);
		return ((d != null) ? d : load).fork();
	}

	private MediaDescriptionCompat.Builder builder() {
		MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
		b.setMediaId(getId());
		return b;
	}

	protected boolean isMediaDescriptionValid(FutureSupplier<MediaDescriptionCompat> d) {
		return d != null;
	}

	protected FutureSupplier<String> buildTitle() {
		BrowsableItem parent = requireNonNull(getParent());
		BrowsableItemPrefs prefs = parent.getPrefs();

		if (prefs.getTitleSeqNumPref()) {
			FutureSupplier<List<Item>> getChildren = parent.getChildren();

			if (getChildren.isDone()) {
				return buildTitle(seqNum, prefs);
			} else {
				Promise<String> load = new Promise<>();
				getChildren.then(children -> {
					assertNotEquals(seqNum, 0);
					return buildTitle(seqNum, prefs);
				}).thenComplete(load);
				buildTitle(0, prefs).onSuccess(t -> load.setProgress(t, 1, 2));
				return load;
			}
		} else {
			return buildTitle(0, prefs);
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Void> updateTitles() {
		MD.set(this, null);
		return completedVoid();
	}

	@Override
	public BrowsableItem getParent() {
		return parent;
	}

	@Override
	public VirtualResource getResource() {
		return file;
	}

	@NonNull
	@Override
	public BrowsableItem getRoot() {
		return requireNonNull(getParent()).getRoot();
	}

	@NonNull
	public MediaLib getLib() {
		return getRoot().getLib();
	}

	@NonNull
	@Override
	public MediaPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		MediaLib lib = getLib();
		if (lib instanceof SharedPreferenceStore)
			return ((SharedPreferenceStore) lib).getSharedPreferences();
		throw new UnsupportedOperationException("Not implemented");
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

	public int getSeqNum() {
		return seqNum;
	}

	public void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		MediaLib lib = getLib();
		if (lib instanceof EventBroadcaster<?>)
			return ((EventBroadcaster<Listener>) lib).getBroadcastEventListeners();
		throw new UnsupportedOperationException("Not implemented");
	}

	protected void reset() {
		description = null;
	}

	protected void set(ItemBase i) {
		FutureSupplier<MediaDescriptionCompat> d = i.description;
		if ((d != null) && d.isDone() && !d.isFailed()) description = d;
		seqNum = i.seqNum;
	}
}
