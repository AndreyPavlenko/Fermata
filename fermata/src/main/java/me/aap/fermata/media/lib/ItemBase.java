package me.aap.fermata.media.lib;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.app.App;
import me.aap.utils.concurrent.CompletableFuture;
import me.aap.utils.function.Consumer;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.TextUtils;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;

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
			StringBuilder sb = TextUtils.getSharedStringBuilder();
			BrowsableItemPrefs prefs = requireNonNull(getParent()).getPrefs();
			boolean nameAppended = false;
			boolean browsable = (this instanceof BrowsableItem);

			if (prefs.getTitleSeqNumPref()) {
				if (seqNum == 0) {
					// Make sure the children are loaded and sorted
					getParent().getChildren();
					MiscUtils.assertTrue(seqNum != 0);
					sb.setLength(0);
				}

				sb.append(seqNum).append(". ");
			}

			if (browsable || prefs.getTitleNamePref()) {
				sb.append(getName());
				nameAppended = true;
			}

			if (!browsable && prefs.getTitleFileNamePref()) {
				MediaFile f = getFile();

				if (f != null) {
					if (nameAppended) sb.append(" - ");
					sb.append(f.getName());
				}
			}

			title = sb.toString();
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
	public MediaDescriptionCompat getMediaDescription(@Nullable Consumer<MediaDescriptionCompat> completionCallback) {
		MediaDescriptionCompat dsc = mediaDescr;

		if (dsc != null) {
			consumeInMainThread(completionCallback, dsc);
			return dsc;
		}

		MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
		Consumer<MediaDescriptionCompat.Builder> builder = buildIncompleteDescription(b);

		if (builder == null) {
			MediaDescriptionCompat d = b.build();
			mediaDescr = d;
			consumeInMainThread(completionCallback, d);
			return d;
		}

		boolean isNew;
		CompletableFuture<MediaDescriptionCompat> load;

		synchronized (this) {
			if (loadDescr == null) {
				isNew = true;
				loadDescr = load = new CompletableFuture<>();
			} else {
				isNew = false;
				load = loadDescr;
			}
		}

		load.addConsumer(completionCallback, this::incomplete, App.get().getHandler());

		if (isMainThread()) {
			MediaDescriptionCompat d = b.build();
			if (isNew) App.get().getExecutor().submit(() -> loadMediaDescription(load, b, builder));
			return d;
		} else {
			return loadMediaDescription(load, b, builder);
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

	private MediaDescriptionCompat loadMediaDescription(CompletableFuture<MediaDescriptionCompat> load,
																											MediaDescriptionCompat.Builder b,
																											Consumer<MediaDescriptionCompat.Builder> builder) {
		if (load.isDone()) return load.get(this::incomplete);

		if (this instanceof PlayableItem) {
			MediaMetadataCompat md = ((PlayableItem) this).getMediaData();
			Bitmap icon = md.getBitmap(METADATA_KEY_ALBUM_ART);
			if (icon == null) icon = requireNonNull(getParent()).getMediaDescription().getIconBitmap();

			if (icon != null) {
				b.setIconBitmap(icon);
			} else {
				String iconUri = md.getString(METADATA_KEY_ALBUM_ART_URI);

				if (iconUri == null) {
					Uri uri = getParent().getMediaDescription().getIconUri();
					if (uri != null) iconUri = uri.toString();
				}

				if (iconUri != null) {
					icon = getLib().getCachedBitmap(iconUri);
					if (icon != null) b.setIconBitmap(icon);
					else b.setIconUri(Uri.parse(iconUri));
				}
			}
		}

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
