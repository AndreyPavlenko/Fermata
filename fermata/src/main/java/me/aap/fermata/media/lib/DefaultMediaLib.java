package me.aap.fermata.media.lib;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.utils.app.App;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.function.Consumer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.UiUtils;

import static me.aap.utils.collection.CollectionUtils.forEach;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.ui.UiUtils.resizedBitmap;

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
	private final Map<String, WeakRef<Item>> itemCache = new HashMap<>();
	private final Map<String, SoftRef<Bitmap>> bitmapCache = new HashMap<>();
	private final ReferenceQueue<Item> itemRefQueue = new ReferenceQueue<>();
	private final ReferenceQueue<Bitmap> bitmapRefQueue = new ReferenceQueue<>();

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

		getFolders().getUnsortedChildren(); // Make sure the roots are loaded and cached

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
			case M3uItem.SCHEME:
				i = M3uItem.create(this, id);
				break;
			case M3uGroupItem.SCHEME:
				i = M3uGroupItem.create(this, id);
				break;
			case M3uTrackItem.SCHEME:
				i = M3uTrackItem.create(this, id);
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
	public void getChildren(String parentMediaId, MediaLibResult<List<MediaItem>> result) {
		if (getRootId().equals(parentMediaId)) {
			Item i = getLastPlayedItem();
			result.detach();
			App.get().execute(() -> {
				List<MediaItem> items = new ArrayList<>(3);
				if (i != null) items.add(i.asMediaItem());
				items.add(getFolders().asMediaItem());
				items.add(getFavorites().asMediaItem());
				items.add(getPlaylists().asMediaItem());
				result.sendResult(items, null);
			});
		} else {
			Item i = getItem(parentMediaId);

			if (i instanceof BrowsableItem) {
				result.detach();
				App.get().execute(() -> result.sendResult(toMediaItems(((BrowsableItem) i).getChildren()), null));
			} else {
				result.sendResult(Collections.emptyList(), null);
			}
		}
	}

	@Override
	public void getItem(String itemId, MediaLibResult<MediaItem> result) {
		Item i = getItem(itemId);

		if (i == null) {
			result.sendResult(null, null);
		} else {
			result.detach();
			App.get().execute(() -> result.sendResult(i.asMediaItem(), null));
		}
	}

	public void search(String query, MediaLibResult<List<MediaItem>> result) {
		if ((query == null) || (query.length() < 3)) {
			result.sendResult(Collections.emptyList(), null);
			return;
		}

		result.detach();
		App.get().execute(() -> {
			Pattern q = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
			List<MediaItem> r = new ArrayList<>();
			forEach(getFolders().getChildren(), i -> i.search(q, r));
			result.sendResult(r, null);
		});
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
		if (i.isVideo()) return i.getPrefs().getPositionPref();
		BrowsableItemPrefs p = i.getParent().getPrefs();
		String id = p.getLastPlayedItemPref();
		return ((id != null) && id.equals(i.getId())) ? p.getLastPlayedPosPref() : 0;
	}

	@Override
	public void setLastPlayed(PlayableItem i, long position) {
		String id;
		BrowsableItemPrefs p;
		long dur = i.getDuration();

		if (dur <= 0) {
			id = i.getId();
			p = i.getParent().getPrefs();
			setLastPlayedItemPref(id);
			setLastPlayedPosPref(0);
			p.setLastPlayedItemPref(id);
			p.setLastPlayedPosPref(0);
			return;
		}

		if ((dur - position) <= 1000) {
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

		if (i.isVideo()) {
			if (position > (dur * 0.9f)) {
				i.getPrefs().setWatchedPref(true);
			} else {
				i.getPrefs().setPositionPref(position);
			}
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

		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
		}
		synchronized (bitmapCache) {
			clearRefs(bitmapCache, bitmapRefQueue);
		}
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

	@Nullable
	@Override
	public Bitmap getCachedBitmap(String uri) {
		synchronized (bitmapCache) {
			clearRefs(bitmapCache, bitmapRefQueue);
			SoftRef<Bitmap> r = bitmapCache.get(uri);

			if (r != null) {
				Bitmap bm = r.get();
				if (bm != null) return bm;
				else bitmapCache.remove(uri);
			}

			return null;
		}
	}

	@Override
	public void getBitmap(String uri, Consumer<Bitmap> consumer) {
		Bitmap bm = getCachedBitmap(uri);
		if (bm != null) consumeInMainThread(consumer, bm);
		else App.get().execute(() -> loadBitmap(uri), consumer);
	}

	private Bitmap loadBitmap(String uri) {
		Bitmap bm = getCachedBitmap(uri);
		if (bm != null) return bm;

		if (uri.startsWith("http://") || uri.startsWith("https://")) {
			try (InputStream in = new URL(uri).openStream()) {
				bm = BitmapFactory.decodeStream(in);
			} catch (Exception ex) {
				Log.d(getClass().getName(), "Failed to load bitmap: " + uri, ex);
			}
		} else if (uri.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
			try {
				Context ctx = getContext();
				Resources res = ctx.getResources();
				String[] s = uri.split("/");
				int id = res.getIdentifier(s[s.length - 1], s[s.length - 2], ctx.getPackageName());
				Drawable d = res.getDrawable(id, ctx.getTheme());
				if (d != null) bm = UiUtils.getBitmap(d, Color.WHITE);
			} catch (Exception ex) {
				Log.e(getClass().getName(), "Failed to load bitmap: " + uri, ex);
			}
		} else {
			ContentResolver cr = getContext().getContentResolver();
			try (ParcelFileDescriptor fd = cr.openFileDescriptor(Uri.parse(uri), "r")) {
				if (fd != null) bm = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
			} catch (Exception ex) {
				if (!uri.startsWith(FileItem.albumArtStrUri)) {
					Log.d(getClass().getName(), "Failed to load bitmap: " + uri, ex);
				}
			}
		}

		return (bm != null) ? cacheBitmap(uri, bm) : null;
	}

	private Bitmap cacheBitmap(String uri, Bitmap bm) {
		bm = resizedBitmap(bm, getMaxBitmapSize());

		synchronized (bitmapCache) {
			clearRefs(bitmapCache, bitmapRefQueue);
			SoftRef<Bitmap> ref = new SoftRef<>(uri, bm, bitmapRefQueue);
			SoftRef<Bitmap> cachedRef = CollectionUtils.putIfAbsent(bitmapCache, uri, ref);

			if (cachedRef != null) {
				Bitmap cached = cachedRef.get();
				if (cached != null) return cached;
				bitmapCache.put(uri, ref);
			}

			return bm;
		}
	}

	private int getMaxBitmapSize() {
		DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
		return Math.min(dm.widthPixels, dm.heightPixels) / 2;
	}

	private static List<MediaItem> toMediaItems(List<? extends Item> items) {
		List<MediaItem> media = new ArrayList<>(items.size());
		forEach(items, i -> media.add(i.asMediaItem()));
		return media;
	}

	Object cacheLock() {
		return itemCache;
	}

	void addToCache(Item i) {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
			if (BuildConfig.DEBUG && itemCache.containsKey(i.getId())) throw new AssertionError();
			itemCache.put(i.getId(), new WeakRef<>(i.getId(), i, itemRefQueue));
		}
	}

	void removeFromCache(Item i) {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
			if (i == null) return;
			CollectionUtils.remove(itemCache, i.getId(), new WeakRef<>(i.getId(), i, itemRefQueue));
		}
	}

	Item getFromCache(String id) {
		synchronized (itemCache) {
			clearRefs(itemCache, itemRefQueue);
			WeakRef<Item> r = itemCache.get(id);

			if (r != null) {
				Item cached = r.get();
				if (cached != null) return cached;
				else CollectionUtils.remove(itemCache, id, r);
			}
		}

		return null;
	}

	@SuppressWarnings("rawtypes")
	private static void clearRefs(Map map, ReferenceQueue q) {
		for (Ref r = (Ref) q.poll(); r != null; r = (Ref) q.poll()) {
			CollectionUtils.remove(map, r.key(), r);
		}
	}

	private interface Ref {
		Object key();
	}

	private static final class WeakRef<V> extends WeakReference<V> implements Ref {
		final Object key;

		public WeakRef(Object key, V value, ReferenceQueue<V> q) {
			super(value, q);
			this.key = key;
		}

		public Object key() {
			return key;
		}
	}

	private static final class SoftRef<V> extends SoftReference<V> implements Ref {
		final Object key;

		public SoftRef(Object key, V value, ReferenceQueue<V> q) {
			super(value, q);
			this.key = key;
		}

		public Object key() {
			return key;
		}
	}
}
