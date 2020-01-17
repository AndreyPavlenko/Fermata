package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaBrowserCompat.MediaItem;

import androidx.annotation.NonNull;
import androidx.media.MediaBrowserServiceCompat;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.pref.SharedPreferenceStore;
import me.aap.fermata.util.BasicEventBroadcaster;
import me.aap.fermata.util.Utils;

import static me.aap.fermata.util.Utils.forEach;

/**
 * @author Andrey Pavlenko
 */
public class DefaultMediaLib extends BasicEventBroadcaster<PreferenceStore.Listener>
		implements MediaLib, MediaLibPrefs, SharedPreferenceStore {
	private static final String ID = "Root";
	private final Context ctx;
	private final SharedPreferences sharedPreferences;
	private final DefaultFolders folders;
	private final DefaultFavorites favorites;
	private final DefaultPlaylists playlists;
	private final Map<String, ItemRef> cache = new HashMap<>();
	private final ReferenceQueue<Item> refQueue = new ReferenceQueue<>();

	public DefaultMediaLib(Context ctx) {
		this.ctx = ctx;
		sharedPreferences = ctx.getSharedPreferences("medialib", Context.MODE_PRIVATE);
		folders = new DefaultFolders(this);
		favorites = new DefaultFavorites(this);
		playlists = new DefaultPlaylists(this);
	}

	public String getRootId() {
		return ID;
	}

	@Override
	public Context getContext() {
		return ctx;
	}

	@Override
	public MediaLibPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}

	@Override
	public Item getItem(CharSequence itemId) {
		String id = itemId.toString();
		Item i = getFromCache(id);
		if (i != null) return i;

		int idx = id.indexOf(':');

		if (idx == -1) {
			switch (id) {
				case DefaultFolders.ID:
					return getFolders();
				case DefaultFavorites.ID:
					return getFavorites();
				case DefaultPlaylists.ID:
					return getPlaylists();
				default:
					return null;
			}
		}

		getFolders().getChildren(null); // Make sure the roots are loaded and cached

		switch (id.substring(0, idx)) {
			case FileItem.SCHEME:
				i = FileItem.create(this, id);
				break;
			case FolderItem.SCHEME:
				i = FolderItem.create(this, id);
				break;
			case CueItem.SCHEME:
				i = CueItem.create(this, id);
				break;
			case CueTrackItem.SCHEME:
				i = CueTrackItem.create(this, id);
				break;
			case DefaultFavorites.SCHEME:
				i = getFavorites().getItem(id);
				break;
			case DefaultPlaylists.SCHEME:
				i = getPlaylists().getItem(id);
				break;
			default:
				return null;
		}

		return i;
	}

	@Override
	public void getChildren(String parentMediaId, MediaBrowserServiceCompat.Result<List<MediaItem>> result) {
		if (getRootId().equals(parentMediaId)) {
			List<MediaItem> items = new ArrayList<>(3);
			Item i = getLastPlayedItem();
			if (i != null) items.add(i.asMediaItem());
			items.add(getFolders().asMediaItem());
			items.add(getFavorites().asMediaItem());
			items.add(getPlaylists().asMediaItem());
			result.sendResult(items);
		} else {
			Item i = getItem(parentMediaId);

			if (i instanceof BrowsableItem) {
				result.detach();
				((BrowsableItem) i).getChildren(c -> result.sendResult(toMediaItems(c)));
			} else {
				result.sendResult(Collections.emptyList());
			}
		}
	}

	public void search(String query, MediaBrowserServiceCompat.Result<List<MediaItem>> result) {
		if ((query == null) || (query.length() < 3)) {
			result.sendResult(Collections.emptyList());
			return;
		}

		Pattern q = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
		List<MediaItem> r = new ArrayList<>();
		forEach(getFolders().getChildren(), i -> i.search(q, r));
		result.sendResult(r);
	}

	@Override
	public PlayableItem getLastPlayedItem() {
		String id = getLastPlayedItemPref();
		Item i = (id == null) ? null : getItem(id);
		return (i instanceof PlayableItem) ? (PlayableItem) i
				: (i instanceof BrowsableItem) ? ((BrowsableItem) i).getFirstPlayable()
				: null;
	}

	@Override
	public long getLastPlayedPosition(PlayableItem i) {
		BrowsableItemPrefs p = i.getParent().getPrefs();
		String id = p.getLastPlayedItemPref();
		return ((id != null) && id.equals(i.getId())) ? p.getLastPlayedPosPref() : 0;
	}

	@Override
	public void setLastPlayed(PlayableItem i, long position) {
		String id;
		BrowsableItemPrefs p;

		if ((i.getDuration() - position) <= 1000) {
			PlayableItem next = i.getNextPlayable();
			position = 0;

			if (next == null) {
				id = i.getId();
				p = i.getParent().getPrefs();
			} else {
				id = next.getId();
				p = next.getParent().getPrefs();
			}
		} else {
			id = i.getId();
			p = i.getParent().getPrefs();
		}

		setLastPlayedItemPref(id);
		setLastPlayedPosPref(position);
		p.setLastPlayedItemPref(id);
		p.setLastPlayedPosPref(position);
	}

	@Override
	public void clearCache() {
		getFolders().clearCache();
		getFavorites().clearCache();
		getPlaylists().clearCache();
		clearRefs();
	}

	@NonNull
	@Override
	public DefaultFolders getFolders() {
		return folders;
	}

	@NonNull
	@Override
	public DefaultFavorites getFavorites() {
		return favorites;
	}

	@NonNull
	@Override
	public DefaultPlaylists getPlaylists() {
		return playlists;
	}


	private static List<MediaItem> toMediaItems(List<? extends DefaultMediaLib.Item> items) {
		List<MediaItem> media = new ArrayList<>(items.size());
		forEach(items, i -> media.add(i.asMediaItem()));
		return media;
	}

	void addToCache(Item i) {
		clearRefs();
		if (BuildConfig.DEBUG && cache.containsKey(i.getId())) throw new AssertionError();
		cache.put(i.getId(), new ItemRef(i, refQueue));
	}

	Item getFromCache(String id) {
		clearRefs();
		ItemRef r = cache.get(id);

		if (r != null) {
			Item cached = r.get();
			if (cached != null) return cached;
			else Utils.remove(cache, id, r);
		}

		return null;
	}

	private void clearRefs() {
		for (ItemRef r = (ItemRef) refQueue.poll(); r != null; r = (ItemRef) refQueue.poll()) {
			Utils.remove(cache, r.id, r);
		}
	}

	private static final class ItemRef extends WeakReference<Item> {
		final String id;

		public ItemRef(Item item, ReferenceQueue<? super Item> q) {
			super(item, q);
			id = item.getId();
		}
	}
}
