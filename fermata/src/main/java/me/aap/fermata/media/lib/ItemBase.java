package me.aap.fermata.media.lib;

import android.content.SharedPreferences;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.app.App;
import me.aap.utils.concurrent.CompletableFuture;
import me.aap.utils.function.Consumer;
import me.aap.utils.misc.Assert;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.SharedTextBuilder;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;
import static me.aap.utils.concurrent.PriorityThreadPool.MAX_PRIORITY;

/**
 * @author Andrey Pavlenko
 */
abstract class ItemBase implements Item, MediaPrefs, SharedPreferenceStore {
	private final String id;
	private final BrowsableItem parent;
	private final MediaFile file;
	private String title;
	private volatile MediaDescriptionCompat mediaDescr;
	private CompletableFuture<MediaDescriptionCompat> loadDescr;
	private int seqNum;

	public ItemBase(String id, @Nullable BrowsableItem parent, MediaFile file) {
		this.id = id.intern();
		this.parent = parent;
		this.file = file;
		DefaultMediaLib lib = getLib();
		//noinspection ConstantConditions
		if (lib != null) lib.addToCache(this);
	}

	@NonNull
	@Override
	public String getId() {
		return id;
	}

	@NonNull
	@Override
	public String getTitle() {
		if (title == null) {
			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				BrowsableItem parent = requireNonNull(getParent());
				BrowsableItemPrefs prefs = parent.getPrefs();
				boolean nameAppended = false;
				boolean browsable = (this instanceof BrowsableItem);

				if (prefs.getTitleSeqNumPref()) {
					if (seqNum == 0) {
						// Make sure the children are loaded and sorted
						parent.getChildren();
						Assert.assertTrue(seqNum != 0);
						tb.setLength(0);
					}

					tb.append(seqNum).append(". ");
				}

				if (browsable || prefs.getTitleNamePref()) {
					tb.append(getName());
					nameAppended = true;
				}

				if (!browsable && prefs.getTitleFileNamePref()) {
					MediaFile f = getFile();

					if (f != null) {
						if (nameAppended) tb.append(" - ");
						tb.append(f.getName());
					}
				}

				title = tb.toString();
			}
		}

		return title;
	}

	@Override
	public BrowsableItem getParent() {
		return parent;
	}

	@Override
	public MediaFile getFile() {
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
	public boolean isMediaDescriptionLoaded() {
		return mediaDescr != null;
	}

	@Override
	public MediaDescriptionCompat getMediaDescription() {
		MediaDescriptionCompat dsc = mediaDescr;
		return (dsc != null) ? dsc : Item.super.getMediaDescription();
	}

	@Override
	public MediaDescriptionCompat getMediaDescription(@Nullable Consumer<MediaDescriptionCompat> consumer) {
		MediaDescriptionCompat dsc = mediaDescr;

		if (dsc != null) {
			consumeInMainThread(consumer, dsc);
			return dsc;
		}

		MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
		Consumer<MediaDescriptionCompat.Builder> builder = buildIncompleteDescription(b);

		if (builder == null) {
			MediaDescriptionCompat d = b.build();
			mediaDescr = d;
			consumeInMainThread(consumer, d);
			return d;
		}

		CompletableFuture<MediaDescriptionCompat> load;

		synchronized (this) {
			if (loadDescr == null) loadDescr = load = new CompletableFuture<>();
			else load = loadDescr;
		}

		if (load.setRunning()) {
			if (isMainThread() && ((consumer == null) || !consumer.canBlockThread())) {
				load.addConsumer(consumer, this::incomplete, App.get().getHandler());
				FermataApplication.get().getExecutor().submit(MAX_PRIORITY, () -> loadMediaDescription(load, builder));
				return b.build();
			} else {
				dsc = loadMediaDescription(load, builder);
				consumeInMainThread(consumer, dsc);
				return dsc;
			}
		} else if (load.isDone()) {
			dsc = load.get(b::build);
			consumeInMainThread(consumer, dsc);
			return dsc;
		} else {
			load.addConsumer(consumer, this::incomplete, App.get().getHandler());
			return b.build();
		}
	}

	private MediaDescriptionCompat loadMediaDescription(CompletableFuture<MediaDescriptionCompat> load,
																											Consumer<MediaDescriptionCompat.Builder> builder) {
		if (load.isDone()) return load.get(this::incomplete);

		MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
		builder.accept(b);
		MediaDescriptionCompat d = b.build();

		if (load.complete(d)) {
			mediaDescr = d;
			synchronized (this) {
				if (loadDescr == load) loadDescr = null;
			}
			return d;
		} else {
			return load.get(this::incomplete);
		}
	}

	Consumer<MediaDescriptionCompat.Builder> buildIncompleteDescription(MediaDescriptionCompat.Builder b) {
		MediaFile f = getFile();
		b.setMediaId(getId()).setTitle((f != null) ? f.getName() : getTitle()).setSubtitle("");
		return this::buildCompleteDescription;
	}

	void buildCompleteDescription(MediaDescriptionCompat.Builder b) {
		b.setMediaId(getId()).setTitle(getTitle()).setSubtitle(getSubtitle());
	}

	private MediaDescriptionCompat incomplete() {
		MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
		buildIncompleteDescription(b);
		return b.build();
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

	public void clearCache() {
		title = null;
		mediaDescr = null;
	}

	@Override
	public void updateTitles() {
		title = null;
		mediaDescr = null;
	}

	void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
		title = null;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}
}
