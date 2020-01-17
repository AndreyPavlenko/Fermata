package me.aap.fermata.media.lib;

import android.content.SharedPreferences;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.pref.SharedPreferenceStore;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.Utils;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
abstract class ItemBase implements Item, MediaPrefs, SharedPreferenceStore {
	private final String id;
	private final BrowsableItem parent;
	private final MediaFile file;
	private String title;
	private MediaDescriptionCompat mediaDescr;
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
			StringBuilder sb = Utils.getSharedStringBuilder();
			BrowsableItemPrefs prefs = requireNonNull(getParent()).getPrefs();
			boolean nameAppended = false;
			boolean browsable = (this instanceof BrowsableItem);

			if (prefs.getTitleSeqNumPref()) {
				if (seqNum == 0) {
					getParent().getChildren();
					Utils.assertTrue(seqNum != 0);
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
	public MediaDescriptionCompat getMediaDescription() {
		if (mediaDescr == null) {
			mediaDescr = getMediaDescriptionBuilder().build();
		}
		return mediaDescr;
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
	}

	public void setSeqNum(int seqNum) {
		this.seqNum = seqNum;
		title = null;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}
}
