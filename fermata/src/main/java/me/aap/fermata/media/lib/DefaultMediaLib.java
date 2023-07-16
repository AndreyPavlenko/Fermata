package me.aap.fermata.media.lib;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaBrowserCompat.MediaItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.engine.MetadataRetriever;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class DefaultMediaLib extends BasicEventBroadcaster<PreferenceStore.Listener>
		implements MediaLib, MediaLibPrefs, SharedPreferenceStore, PreferenceStore.Listener {
	private static final String ID = "Root";
	private final Context ctx;
	private final SharedPreferences sharedPreferences;
	private final DefaultFolders folders;
	private final DefaultFavorites favorites;
	private final DefaultPlaylists playlists;
	private final MediaEngineManager mediaEngineManager;
	private final MetadataRetriever metadataRetriever;
	private final Map<String, WeakRef<Item>> itemCache = new HashMap<>();
	private final ReferenceQueue<Item> itemRefQueue = new ReferenceQueue<>();
	@Nullable
	private final AtvInterface atvInterface;

	public DefaultMediaLib(Context ctx) {
		this.ctx = ctx;
		sharedPreferences = ctx.getSharedPreferences("medialib", Context.MODE_PRIVATE);
		mediaEngineManager = new MediaEngineManager(this);
		metadataRetriever = new MetadataRetriever(mediaEngineManager);
		folders = new DefaultFolders(this);
		favorites = new DefaultFavorites(this);
		playlists = new DefaultPlaylists(this);
		atvInterface = AtvInterface.create(this);
		addBroadcastListener(this);
	}

	@NonNull
	public String getRootId() {
		return ID;
	}

	@NonNull
	@Override
	public Context getContext() {
		return ctx;
	}

	@NonNull
	@Override
	public MediaLibPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}

	@NonNull
	@Override
	public FutureSupplier<? extends Item> getItem(CharSequence itemId) {
		String id = itemId.toString();
		Item i = getFromCache(id);
		if (i != null) return completed(i);

		int idx = id.indexOf(':');

		if (idx == -1) {
			switch (id) {
				case DefaultFolders.ID:
					return completed(getFolders());
				case DefaultFavorites.ID:
					return completed(getFavorites());
				case DefaultPlaylists.ID:
					return completed(getPlaylists());
				default:
					FutureSupplier<? extends Item> ai = AddonManager.get().getItem(this, null, id);
					return (ai != null) ? ai : completedNull();
			}
		}

		try {
			String scheme = id.substring(0, idx);

			switch (scheme) {
				case FileItem.SCHEME:
					return FileItem.create(this, id);
				case FolderItem.SCHEME:
					return FolderItem.create(this, id);
				case CueItem.SCHEME:
					return CueItem.create(this, id);
				case CueTrackItem.SCHEME:
					return CueTrackItem.create(this, id);
				case M3uItem.SCHEME:
					return M3uItem.create(this, id);
				case M3uGroupItem.SCHEME:
					return M3uGroupItem.create(this, id);
				case M3uTrackItem.SCHEME:
					return M3uTrackItem.create(this, id);
				case DefaultFavorites.SCHEME:
					return getFavorites().getItem(id);
				case DefaultPlaylists.SCHEME:
					return getPlaylists().getItem(id);
				default:
					FutureSupplier<? extends Item> ai = AddonManager.get().getItem(this, scheme, id);
					return (ai != null) ? ai : completedNull();
			}
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	@Nullable
	@Override
	public Item getCachedItem(CharSequence id) {
		synchronized (cacheLock()) {
			return getFromCache(id.toString());
		}
	}

	@Nullable
	@Override
	public Item getOrCreateCachedItem(CharSequence id, Function<String, ? extends Item> create) {
		synchronized (cacheLock()) {
			String iid = id.toString();
			Item i = getFromCache(iid);
			if (i != null) return i;
			i = create.apply(iid);
			itemCache.put(iid, new WeakRef<>(id, i, itemRefQueue));
			return i;
		}
	}

	@Override
	public void getChildren(String parentMediaId, MediaLibResult<List<MediaItem>> result) {
		result.detach();

		if (getRootId().equals(parentMediaId)) {
			List<MediaItem> items = new ArrayList<>(4);
			getLastPlayedItem().then(i -> (i == null) ? completedNull() : i.asMediaItem())
					.onSuccess(i -> {
						if (i != null) items.add(i);
					}).then(v -> getFolders().asMediaItem()).onSuccess(items::add)
					.then(v -> getFavorites().asMediaItem()).onSuccess(items::add)
					.then(v -> getPlaylists().asMediaItem()).onSuccess(items::add)
					.onCompletion((r, f) -> result.sendResult(items, null)).onFailure(this::log);
		} else {
			getItem(parentMediaId).then(i -> {
				if (i instanceof BrowsableItem) {
					return ((BrowsableItem) i).getChildren().then(children -> {
						if (children.isEmpty()) return completedEmptyList();
						List<MediaItem> items = new ArrayList<>(children.size());
						return Async.forEach(c -> c.asMediaItem().map(items::add), children).map(r -> items);
					});
				} else {
					return completedEmptyList();
				}
			}).onFailure(this::log).onCompletion((r, f) -> result.sendResult(r, null));
		}
	}

	@Override
	public void getItem(String itemId, MediaLibResult<MediaItem> result) {
		result.detach();
		getItem(itemId).then(Item::asMediaItem).onFailure(this::log)
				.onCompletion((i, f) -> result.sendResult(i, null));
	}

	@Override
	public void search(String query, MediaLibResult<List<MediaItem>> result) {
		getMetadataRetriever().queryId(query).onCompletion((id, err) -> {
			if (id != null) {
				getItem(id).onCompletion((i, err1) -> {
					if (i == null) result.sendResult(Collections.emptyList(), null);
					else i.asMediaItem().onCompletion((mi, err2) -> result.sendResult(
							(mi == null) ? Collections.emptyList() : Collections.singletonList(mi), null));
				});
			} else {
				result.sendResult(Collections.emptyList(), null);
			}
		});
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getLastPlayedItem() {
		String id = getLastPlayedItemPref();
		if (id == null) return completedNull();
		return getItem(id).then(i -> {
			if (i instanceof PlayableItem) return completed((PlayableItem) i);
			if (i instanceof BrowsableItem) return ((BrowsableItem) i).getFirstPlayable();
			return completedNull();
		});
	}

	@Override
	public long getLastPlayedPosition(PlayableItem i) {
		long pos = i.getPrefs().getPositionPref();
		if ((pos != 0) || i.isVideo()) return pos;
		BrowsableItemPrefs p = i.getParent().getPrefs();
		String id = p.getLastPlayedItemPref();
		return ((id != null) && id.equals(i.getId())) ? p.getLastPlayedPosPref() : 0;
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

	@NonNull
	@Override
	public FermataVfsManager getVfsManager() {
		return folders.getVfsManager();
	}

	@NonNull
	@Override
	public MediaEngineManager getMediaEngineManager() {
		return mediaEngineManager;
	}

	@NonNull
	@Override
	public MetadataRetriever getMetadataRetriever() {
		return metadataRetriever;
	}

	public void getAtvInterface(Consumer<AtvInterface> c) {
		if (atvInterface != null) c.accept(atvInterface);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (prefs.contains(BrowsableItemPrefs.SHOW_TRACK_ICONS)) {
			synchronized (itemCache) {
				for (Iterator<WeakRef<Item>> it = itemCache.values().iterator(); it.hasNext(); ) {
					WeakRef<Item> r = it.next();
					Item i = r.get();
					if (i == null) it.remove();
					else i.updateTitles();
				}
			}
		}
	}

	public Object cacheLock() {
		return itemCache;
	}

	void addToCache(Item i) {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
			String id = i.getId();
			if (BuildConfig.D && itemCache.containsKey(id)) {
				throw new AssertionError(
						"Unable to add item " + i + ". Item with id=" + id + "already exists: " +
								itemCache.get(id));
			}
			itemCache.put(id, new WeakRef<>(id, i, itemRefQueue));
		}
	}

	public void removeFromCache(Item i) {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
			if (i == null) return;
			String id = i.getId();
			WeakRef<Item> r = itemCache.get(id);
			if (r == null) return;
			Item cached = r.get();
			if ((cached == null) || (cached == i)) itemCache.remove(id);
		}
	}

	public Item getFromCache(String id) {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
			WeakRef<Item> r = itemCache.get(id);

			if (r != null) {
				Item cached = r.get();
				if (cached != null) return cached;
				else itemCache.remove(id);
			}
		}

		return null;
	}

	@Override
	public void clearCache() {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
		}
	}

	public void cleanUpPrefs() {
		metadataRetriever.getBitmapCache().cleanUpPrefs();
		SharedPreferences prefs = getSharedPreferences();
		List<String> keys = new ArrayList<>(prefs.getAll().keySet());
		Set<String> names = new HashSet<>();
		getPrefNames(PlayableItemPrefs.class, names);
		getPrefNames(BrowsableItemPrefs.class, names);
		getPrefNames(StreamItemPrefs.class, names);

		Async.forEach(k -> {
			int idx = k.lastIndexOf('#');

			if ((idx <= 0) || (idx == k.length() - 1) || !names.contains(k.substring(idx + 1))) {
				return completedVoid();
			} else {
				return getItem(k.substring(0, idx)).then(i -> {
					if (i == null) {
						Log.i("Item not found - removing preference key ", k);
						prefs.edit().remove(k).apply();
						return completedVoid();
					} else {
						VirtualResource r = i.getResource();
						if (r == null) return completedVoid();
						return r.exists().then(exists -> {
							if (!exists) {
								Log.i("Resource does not exist - removing preference key ", k);
								prefs.edit().remove(k).apply();
							}
							return completedVoid();
						});
					}
				});
			}
		}, keys);
	}

	private static void getPrefNames(Class<?> c, Set<String> names) {
		try {
			for (Field f : c.getDeclaredFields()) {
				if (Pref.class.isAssignableFrom(f.getType())) {
					Pref<?> p = (Pref<?>) f.get(null);
					if (p != null) names.add(p.getName());
				}
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to get field names from ", c);
		}
	}

	@SuppressWarnings("rawtypes")
	private static void clearRefs(Map map, ReferenceQueue q) {
		for (WeakRef r = (WeakRef) q.poll(); r != null; r = (WeakRef) q.poll()) {
			CollectionUtils.remove(map, r.key, r);
		}
	}

	private void log(Throwable ex) {
		Log.e(ex, "Error occurred");
	}

	private static final class WeakRef<V> extends WeakReference<V> {
		final Object key;

		public WeakRef(Object key, V value, ReferenceQueue<V> q) {
			super(value, q);
			this.key = key;
		}
	}
}
