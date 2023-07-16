package me.aap.fermata.media.lib;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_MASK_ALL;
import static me.aap.fermata.media.pref.BrowsableItemPrefs.SORT_MASK_NAME_RND;
import static me.aap.fermata.util.Utils.getResourceUri;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.BitmapCache;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.engine.MetadataRetriever;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.fermata.provider.FermataContentProvider;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.async.Async;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.function.Predicate;
import me.aap.utils.holder.IntHolder;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLib {

	@NonNull
	String getRootId();

	@NonNull
	Context getContext();

	@NonNull
	MediaLibPrefs getPrefs();

	@NonNull
	Folders getFolders();

	@NonNull
	Favorites getFavorites();

	@NonNull
	Playlists getPlaylists();

	@NonNull
	FermataVfsManager getVfsManager();

	@NonNull
	MediaEngineManager getMediaEngineManager();

	@NonNull
	MetadataRetriever getMetadataRetriever();

	void getAtvInterface(Consumer<AtvInterface> c);

	default BitmapCache getBitmapCache() {
		return getMetadataRetriever().getBitmapCache();
	}

	@NonNull
	default FutureSupplier<Bitmap> getBitmap(String uri, boolean cache, boolean resize) {
		return getBitmapCache().getBitmap(getContext(), uri, cache, resize);
	}

	@NonNull
	default FutureSupplier<Bitmap> getBitmap(String uri) {
		return getBitmap(uri, true, false);
	}

	@NonNull
	FutureSupplier<? extends Item> getItem(CharSequence id);

	@NonNull
	FutureSupplier<PlayableItem> getLastPlayedItem();

	@Nullable
	Item getCachedItem(CharSequence id);

	@Nullable
	Item getOrCreateCachedItem(CharSequence id, Function<String, ? extends Item> create);

	long getLastPlayedPosition(PlayableItem i);

	void getChildren(String parentMediaId, MediaLibResult<List<MediaItem>> result);

	default void getChildren(String parentMediaId, MediaBrowserServiceCompat.Result<List<MediaItem>> result) {
		getChildren(parentMediaId, new MediaLibResult.Wrapper<>(result));
	}

	void getItem(String itemId, MediaLibResult<MediaItem> result);

	default void getItem(String itemId, MediaBrowserServiceCompat.Result<MediaItem> result) {
		getItem(itemId, new MediaLibResult.Wrapper<>(result));
	}

	void search(String query, MediaLibResult<List<MediaItem>> result);

	default void search(String query, MediaBrowserServiceCompat.Result<List<MediaItem>> result) {
		search(query, new MediaLibResult.Wrapper<>(result));
	}

	default void clearCache() {
	}

	interface Item {

		@NonNull
		String getId();

		VirtualResource getResource();

		@NonNull
		default String getName() {
			VirtualResource r = getResource();
			return (r != null) ? r.getName() : getClass().getSimpleName();
		}

		@NonNull
		MediaLib getLib();

		@Nullable
		BrowsableItem getParent();

		@NonNull
		BrowsableItem getRoot();

		@NonNull
		MediaPrefs getPrefs();

		@DrawableRes
		int getIcon();

		@NonNull
		FutureSupplier<Uri> getIconUri();

		@NonNull
		FutureSupplier<MediaDescriptionCompat> getMediaDescription();

		default boolean isExternal() {
			return false;
		}

		default FutureSupplier<MediaDescriptionCompat> getMediaItemDescription() {
			return getMediaDescription().then(md -> {
				if (getParent() == null) {
					MediaLib lib = getLib();
					return lib.getBitmap(getResourceUri(lib.getContext(), getIcon()).toString(),
							true, true).map(bm -> {
						MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
						b.setMediaId(md.getMediaId());
						b.setTitle(md.getTitle());
						b.setSubtitle(md.getSubtitle());
						b.setIconBitmap(bm);
						return b.build();
					});
				}


				Uri uri = md.getIconUri();
				if (uri == null) return completed(md);

				MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
				b.setMediaId(md.getMediaId());
				b.setTitle(md.getTitle());
				b.setSubtitle(md.getSubtitle());

				if (FermataContentProvider.isSupportedFileScheme(uri.getScheme())) {
					b.setIconUri(FermataContentProvider.toImgUri(uri));
				}

				return completed(b.build());
			});
		}

		@NonNull
		default FutureSupplier<Long> getQueueId() {
			BrowsableItem p = getParent();
			if (p == null) return completed((long) MediaSession.QueueItem.UNKNOWN_ID);

			return p.getChildren().map(children -> {
				int i = children.indexOf(this);
				return (long) (i == -1 ? MediaSession.QueueItem.UNKNOWN_ID : i);
			});
		}

		@NonNull
		default FutureSupplier<MediaItem> asMediaItem() {
			return getMediaItemDescription().map(md -> new MediaItem(md, (this instanceof BrowsableItem) ?
					MediaItem.FLAG_BROWSABLE : MediaItem.FLAG_PLAYABLE));
		}

		@NonNull
		default FutureSupplier<PlayableItem> getPrevPlayable() {
			return getPlayable(false);
		}

		@NonNull
		default FutureSupplier<PlayableItem> getNextPlayable() {
			return getPlayable(true);
		}

		@NonNull
		default FutureSupplier<PlayableItem> getPlayable(boolean next) {
			BrowsableItem parent = getParent();
			if (parent == null) return completedNull();
			BrowsableItemPrefs parentPrefs = parent.getPrefs();

			if (parentPrefs.getShufflePref()) {
				return parent.getShuffleIterator().map(it -> it.hasNext() ? it.next() : null);
			}

			if (this instanceof PlayableItem) {
				String repeat = parentPrefs.getRepeatItemPref();
				if ((repeat != null) && (repeat.equals(getId()))) return completed((PlayableItem) this);
			}

			return parent.getChildren().then(children -> {
				int size = children.size();

				if (size > 0) {
					Item i = null;
					int idx = children.indexOf(this);

					if (idx != -1) {
						if (next) {
							if (idx < (size - 1)) {
								i = children.get(idx + 1);
							} else if (parentPrefs.getRepeatPref()) {
								i = children.get(0);
							}
						} else {
							if (idx > 0) {
								i = children.get(idx - 1);
							} else if (parentPrefs.getRepeatPref()) {
								i = children.get(size - 1);
							}
						}
					}

					if (i instanceof PlayableItem) {
						return completed((PlayableItem) i);
					} else if (i instanceof BrowsableItem) {
						BrowsableItem br = (BrowsableItem) i;
						return br.getFirstPlayable().then(pi -> {
							if (pi != null) return completed(pi);
							else return br.getPlayable(next);
						});
					}
				}

				return parent.getPlayable(next);
			});
		}

		@NonNull
		default FutureSupplier<Void> updateTitles() {
			return completedVoid();
		}

		default boolean addChangeListener(ChangeListener l) {
			return false;
		}

		default boolean removeChangeListener(ChangeListener l) {
			return false;
		}

		interface ChangeListener {
			void mediaItemChanged(Item i);
		}
	}

	interface PlayableItem extends Item {
		@NonNull
		PlayableItemPrefs getPrefs();

		boolean isVideo();

		default boolean isSeekable() {
			if (isStream()) return false;
			VirtualFileSystem.Provider p = getResource().getVirtualFileSystem().getProvider();
			return !(p instanceof GenericFileSystem.Provider);
		}

		@NonNull
		FutureSupplier<MediaMetadataCompat> getMediaData();

		@NonNull
		@Override
		BrowsableItem getParent();

		@NonNull
		PlayableItem export(String exportId, BrowsableItem parent);

		String getOrigId();

		@NonNull
		default Uri getLocation() {
			VirtualResource file = getResource();
			if (!isNetResource()) return file.getRid().toAndroidUri();
			else return getLib().getVfsManager().getHttpRid(file).toAndroidUri();
		}

		default boolean isNetResource() {
			VirtualResource file = getResource();
			return !file.isLocalFile() && !(file.getVirtualFileSystem() instanceof GenericFileSystem);
		}

		default boolean isStream() {
			return false;
		}

		@NonNull
		default FutureSupplier<Long> getDuration() {
			return getMediaData().map(md -> md.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
		}

		@NonNull
		default FutureSupplier<Void> setDuration(long duration) {
			return completedVoid();
		}

		default boolean isTimerRequired() {
			return false;
		}

		@DrawableRes
		@Override
		default int getIcon() {
			return isVideo() ? R.drawable.video : R.drawable.audiotrack;
		}

		@NonNull
		@Override
		default FutureSupplier<Uri> getIconUri() {
			BrowsableItem p = getParent();
			if (!p.getPrefs().getShowTrackIconsPref()) return completedNull();

			return getMediaData().then(md -> {
				String u = md.getString(METADATA_KEY_ALBUM_ART_URI);
				return (u != null) ? completed(Uri.parse(u)) : p.getIconUri();
			});
		}

		default long getOffset() {
			return 0;
		}

		default boolean isFavoriteItem() {
			return getLib().getFavorites().isFavoriteItem(this);
		}

		default boolean isRepeatItemEnabled() {
			return getId().equals(getParent().getPrefs().getRepeatItemPref());
		}

		default void setRepeatItemEnabled(boolean enabled) {
			getParent().getPrefs().setRepeatItemPref(enabled ? getId() : null);
		}

		default boolean isLastPlayed() {
			return getId().equals(getParent().getPrefs().getLastPlayedItemPref());
		}

		@Nullable
		default String getUserAgent() {
			return null;
		}
	}

	interface StreamItem extends PlayableItem, BrowsableItem {
		String STREAM_START_TIME = "me.aap.media.stream.START_TIME";
		String STREAM_END_TIME = "me.aap.media.stream.END_TIME";

		@NonNull
		@Override
		StreamItemPrefs getPrefs();

		@Override
		default boolean isStream() {
			return true;
		}

		default boolean isSeekable(long time) {
			return false;
		}

		@Nullable
		default Uri getLocation(long time, long duration) {
			return null;
		}

		@NonNull
		default <E extends EpgItem> FutureSupplier<List<E>> getEpg() {
			return Completed.completedEmptyList();
		}

		@NonNull
		default <E extends EpgItem> FutureSupplier<E> getEpg(long time) {
			return getEpg().map(l -> {
				int idx = CollectionUtils.binarySearch(l, e -> {
					if (time < e.getStartTime()) return 1;
					if (time < e.getEndTime()) return 0;
					return -1;
				});
				//noinspection unchecked
				return (idx < 0) ? null : (E) l.get(idx);
			});
		}

		@NonNull
		@Override
		default FutureSupplier<Long> getDuration() {
			return getMediaDescription().then(md -> {
				Bundle b = md.getExtras();
				if (b != null) {
					long start = b.getLong(STREAM_START_TIME, 0);
					if (start != 0) {
						long end = b.getLong(STREAM_END_TIME, 0);
						if (end > start) return completed(end - start);
					}
				}
				return PlayableItem.super.getDuration();
			});
		}

		@DrawableRes
		@Override
		default int getIcon() {
			return PlayableItem.super.getIcon();
		}

		@NonNull
		@Override
		default FutureSupplier<Uri> getIconUri() {
			return PlayableItem.super.getIconUri();
		}

		@NonNull
		@Override
		@SuppressWarnings("unchecked")
		default FutureSupplier<List<EpgItem>> getChildren() {
			return getUnsortedChildren();
		}

		@NonNull
		@Override
		@SuppressWarnings("unchecked")
		default FutureSupplier<List<EpgItem>> getUnsortedChildren() {
			return getEpg();
		}

		@NonNull
		@Override
		default FutureSupplier<Iterator<PlayableItem>> getShuffleIterator() {
			return completed(Collections.emptyIterator());
		}
	}

	interface EpgItem extends Item, Comparable<EpgItem> {

		@NonNull
		@Override
		StreamItem getParent();

		long getStartTime();

		long getEndTime();

		EpgItem getPrev();

		EpgItem getNext();

		@DrawableRes
		@Override
		default int getIcon() {
			return R.drawable.epg;
		}

		@Override
		default int compareTo(EpgItem o) {
			return Long.compare(getStartTime(), o.getStartTime());
		}
	}

	interface ArchiveItem extends PlayableItem, EpgItem {

		long getExpirationTime();

		default boolean isExpired() {
			return getExpirationTime() <= System.currentTimeMillis();
		}

		default boolean isStream() {
			return true;
		}

		@Override
		default int getIcon() {
			return EpgItem.super.getIcon();
		}

		@Nullable
		@Override
		default String getUserAgent() {
			return getParent().getUserAgent();
		}
	}

	interface BrowsableItem extends Item {

		@NonNull
		BrowsableItemPrefs getPrefs();

		@NonNull
		<I extends Item> FutureSupplier<List<I>> getChildren();

		@NonNull
		<I extends Item> FutureSupplier<List<I>> getUnsortedChildren();

		@NonNull
		default FutureSupplier<List<PlayableItem>> getPlayableChildren(boolean recursive) {
			return getPlayableChildren(recursive, true);
		}

		@NonNull
		default FutureSupplier<List<PlayableItem>> getPlayableChildren(boolean recursive, boolean sorted) {
			return getPlayableChildren(recursive, sorted, Integer.MAX_VALUE);
		}

		@NonNull
		default FutureSupplier<List<PlayableItem>> getPlayableChildren(boolean recursive, boolean sorted, int max) {
			return getChildren(recursive, sorted, max, PlayableItem.class::isInstance, PlayableItem.class::cast);
		}

		@NonNull
		default <C extends Item> FutureSupplier<List<C>> getChildren(boolean recursive, boolean sorted, int max,
																																 Predicate<? super Item> predicate, Function<? super Item, C> map) {
			FutureSupplier<List<Item>> list = sorted ? getChildren() : getUnsortedChildren();

			if (!recursive) {
				return list.map(children -> {
					List<C> ci = new ArrayList<>(Math.min(max, children.size()));
					for (Item i : children) {
						if (predicate.test(i)) {
							ci.add(map.apply(i));
							if (ci.size() >= max) break;
						}
					}
					return ci;
				});
			}

			List<C> ci = list.isDone() ? new ArrayList<>(list.get(Collections::emptyList).size()) : new ArrayList<>();
			Queue<BrowsableItem> br = new LinkedList<>();

			return list.thenIterate(children -> {
				for (Item i : children.get()) {
					if (predicate.test(i)) {
						ci.add(map.apply(i));
						if (ci.size() >= max) return null;
					}

					if ((i instanceof BrowsableItem) && !(i instanceof StreamItem)) br.add((BrowsableItem) i);
				}

				return (br.isEmpty()) ? null : requireNonNull(br.poll()).getChildren();
			}).map(children -> ci);
		}

		@NonNull
		FutureSupplier<Iterator<PlayableItem>> getShuffleIterator();

		@DrawableRes
		@Override
		default int getIcon() {
			return me.aap.utils.R.drawable.folder;
		}

		@NonNull
		default FutureSupplier<Uri> getIconUri() {
			return completedNull();
		}

		@NonNull
		default FutureSupplier<List<QueueItem>> getQueue() {
			return getChildren().then(list -> {
				if (list.isEmpty()) return Completed.completedEmptyList();
				IntHolder i = new IntHolder();
				List<QueueItem> items = new ArrayList<>(list.size());
				return Async.forEach(c -> c.getMediaItemDescription().then(d -> {
					items.add(new QueueItem(d, i.value++));
					return completedVoid();
				}), list).map(v -> items);
			});
		}

		@NonNull
		default FutureSupplier<PlayableItem> getFirstPlayable() {
			return getPlayableChildren(true, true, 1).map(items -> items.isEmpty() ? null : items.get(0));
		}

		@NonNull
		default FutureSupplier<PlayableItem> getLastPlayedItem() {
			String id = getPrefs().getLastPlayedItemPref();
			if (id == null) return completedNull();

			return getUnsortedChildren().map(children -> {
				for (Item i : children) {
					if ((i instanceof PlayableItem) && id.equals(i.getId())) return (PlayableItem) i;
				}
				return null;
			});
		}

		default boolean sortChildrenEnabled() {
			return true;
		}

		@NonNull
		default FutureSupplier<Void> refresh() {
			return completedVoid();
		}

		@NonNull
		default FutureSupplier<Void> rescan() {
			return completedVoid();
		}

		@NonNull
		default FutureSupplier<Void> updateSorting() {
			return completedVoid();
		}

		default int getSupportedSortOpts() {
			VirtualResource res = getResource();
			return (res instanceof VirtualFolder) ? SORT_MASK_ALL : SORT_MASK_NAME_RND;
		}
	}

	interface Folders extends BrowsableItem {

		boolean isFoldersItemId(String id);

		@Override
		default int getIcon() {
			return me.aap.utils.R.drawable.folder;
		}

		@NonNull
		FutureSupplier<Item> addItem(Uri uri);

		FutureSupplier<Void> removeItem(int idx);

		FutureSupplier<Void> removeItem(Item item);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> moveItem(int fromPosition, int toPosition);
	}

	interface Favorites extends BrowsableItem {

		boolean isFavoriteItem(PlayableItem i);

		boolean isFavoriteItemId(String id);

		FutureSupplier<Void> addItem(PlayableItem i);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> addItems(List<PlayableItem> items);

		FutureSupplier<Void> removeItem(int idx);

		FutureSupplier<Void> removeItem(PlayableItem i);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> removeItems(List<PlayableItem> items);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> moveItem(int fromPosition, int toPosition);

		@Override
		default int getIcon() {
			return R.drawable.favorite_filled;
		}
	}

	interface Playlists extends BrowsableItem {

		boolean isPlaylistsItemId(String id);

		FutureSupplier<Playlist> addItem(CharSequence name);

		FutureSupplier<Void> removeItem(int idx);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> removeItems(List<Playlist> items);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> moveItem(int fromPosition, int toPosition);

		@Override
		default int getIcon() {
			return R.drawable.playlist;
		}
	}

	interface Playlist extends BrowsableItem {

		@NonNull
		@Override
		Playlists getParent();

		FutureSupplier<Void> addItems(List<PlayableItem> items);

		FutureSupplier<Void> removeItem(int idx);

		FutureSupplier<Void> removeItems(List<PlayableItem> items);

		@SuppressWarnings("UnusedReturnValue")
		FutureSupplier<Void> moveItem(int fromPosition, int toPosition);

		@Override
		default int getIcon() {
			return R.drawable.playlist;
		}
	}
}
