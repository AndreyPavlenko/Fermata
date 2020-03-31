package me.aap.fermata.media.lib;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.collection.NaturalOrderComparator;
import me.aap.utils.concurrent.CompletableFuture;
import me.aap.utils.concurrent.FutureSupplier;
import java.util.function.Consumer;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLib {

	String getRootId();

	Context getContext();

	MediaLibPrefs getPrefs();

	@NonNull
	Folders getFolders();

	@NonNull
	Favorites getFavorites();

	@NonNull
	Playlists getPlaylists();

	@Nullable
	Bitmap getCachedBitmap(String uri);

	void getBitmap(String uri, Consumer<Bitmap> consumer);

	default Bitmap getBitmap(String uri) {
		CompletableFuture<Bitmap> f = new CompletableFuture<>();
		getBitmap(uri, f);
		return f.get(() -> null);
	}

	@Nullable
	Item getItem(CharSequence id);

	@Nullable
	PlayableItem getLastPlayedItem();

	long getLastPlayedPosition(PlayableItem i);

	void setLastPlayed(PlayableItem i, long position);

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

	default <T> CompletableFuture<T> newCompletableFuture() {
		long timeout = BuildConfig.DEBUG ? 0 : 10;
		return new CompletableFuture<>(timeout, TimeUnit.SECONDS);
	}

	interface Item extends Comparable<Item> {

		@NonNull
		String getId();

		@NonNull
		String getTitle();

		@NonNull
		String getSubtitle();

		int getIcon();

		MediaFile getFile();

		@NonNull
		MediaLib getLib();

		@Nullable
		BrowsableItem getParent();

		@NonNull
		BrowsableItem getRoot();

		MediaPrefs getPrefs();

		boolean isMediaDescriptionLoaded();

		MediaDescriptionCompat getMediaDescription(@Nullable Consumer<MediaDescriptionCompat> completionCallback);

		default MediaDescriptionCompat getMediaDescription() {
			CompletableFuture<MediaDescriptionCompat> f = getLib().newCompletableFuture();
			MediaDescriptionCompat dsc = getMediaDescription(f);

			try {
				return f.get();
			} catch (Exception ex) {
				Log.e(getClass().getName(), "Failed to get media description", ex);
				return dsc;
			}
		}

		default String getName() {
			MediaFile f = getFile();
			return (f != null) ? f.getName() : getTitle();
		}

		default long getQueueId() {
			BrowsableItem p = getParent();
			if (p == null) return MediaSession.QueueItem.UNKNOWN_ID;
			int i = p.getChildren().indexOf(this);
			return i == -1 ? MediaSession.QueueItem.UNKNOWN_ID : i;
		}


		default MediaItem asMediaItem() {
			MediaDescriptionCompat md = getMediaDescription();
			boolean browsable = (this instanceof BrowsableItem);

			if ((md.getIconUri() != null) || (md.getIconBitmap() != null)) {
				MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
				b.setMediaId(md.getMediaId());
				b.setTitle(md.getTitle());
				b.setSubtitle(md.getSubtitle());

				if (browsable || ((PlayableItem) this).isStream()) {
					if (md.getIconBitmap() != null) b.setIconBitmap(md.getIconBitmap());
					else b.setIconBitmap(getLib().getBitmap(md.getIconUri().toString()));
				}

				md = b.build();
			}

			return new MediaItem(md, browsable ? MediaItem.FLAG_BROWSABLE : MediaItem.FLAG_PLAYABLE);
		}

		@Override
		default int compareTo(Item o) {
			return NaturalOrderComparator.compareNatural(getName(), o.getName());
		}

		default void clearCache() {
		}

		default void updateTitles() {
		}
	}

	interface PlayableItem extends Item {

		PlayableItemPrefs getPrefs();

		boolean isVideo();

		default boolean isStream() {
			return !getFile().isLocalFile();
		}

		boolean isMediaDataLoaded();

		FutureSupplier<MediaMetadataCompat> getMediaData(@Nullable Consumer<MediaMetadataCompat> completionCallback);

		default MediaMetadataCompat getMediaData() {
			return getMediaData(null).get(() -> new MediaMetadataCompat.Builder().build());
		}

		@NonNull
		@Override
		BrowsableItem getParent();

		Uri getLocation();

		PlayableItem export(String exportId, BrowsableItem parent);

		String getOrigId();

		long getDuration();

		default void setDuration(long duration) {
		}

		default boolean isTimerRequired() {
			return false;
		}

		default String getName() {
			String name = getMediaData().getString(MediaMetadataCompat.METADATA_KEY_TITLE);
			return (name == null) || name.isEmpty() ? getFile().getName() : name;
		}

		@Override
		default int getIcon() {
			if (isVideo()) {
				return getPrefs().getWatchedPref() ? R.drawable.watched_video : R.drawable.video;
			} else {
				return R.drawable.audiotrack;
			}
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

		default PlayableItem getPrevPlayable() {
			return getPlayable(false);
		}

		default PlayableItem getNextPlayable() {
			return getPlayable(true);
		}

		default PlayableItem getPlayable(boolean next) {
			BrowsableItem parent = getParent();
			BrowsableItemPrefs parentPrefs = parent.getPrefs();

			if (parentPrefs.getShufflePref()) {
				Iterator<PlayableItem> shuffle = parent.getShuffleIterator();
				if (shuffle.hasNext()) return shuffle.next();
			}

			String repeat = parentPrefs.getRepeatItemPref();
			if ((repeat != null) && (repeat.equals(getId()))) return this;

			List<? extends Item> children = parent.getChildren();
			int size = children.size();
			Item i = null;

			if (size > 0) {
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
					return (PlayableItem) i;
				} else if (i instanceof BrowsableItem) {
					PlayableItem pi = ((BrowsableItem) i).getFirstPlayable();
					if (pi != null) return pi;
				}
			}

			for (BrowsableItem p = parent.getParent(); p != null; parent = p, p = p.getParent()) {
				children = p.getChildren();
				size = children.size();

				if (size > 0) {
					int idx = children.indexOf(parent);

					if (idx != -1) {
						if (next) {
							if (idx < (size - 1)) {
								i = children.get(idx + 1);
							} else if (p.getPrefs().getRepeatPref()) {
								i = children.get(0);
							}
						} else {
							if (idx > 0) {
								i = children.get(idx - 1);
							} else if (p.getPrefs().getRepeatPref()) {
								i = children.get(size - 1);
							}
						}

						if (i instanceof PlayableItem) {
							return (PlayableItem) i;
						} else if (i instanceof BrowsableItem) {
							PlayableItem pi = ((BrowsableItem) i).getFirstPlayable();
							if (pi != null) return pi;
						}
					}
				}
			}

			return null;
		}

		default boolean isLastPlayed() {
			return getId().equals(getParent().getPrefs().getLastPlayedItemPref());
		}
	}

	interface BrowsableItem extends Item {

		BrowsableItemPrefs getPrefs();

		void getChildren(
				// This consumer receives unsorted list of items without metadata
				@Nullable Consumer<List<? extends Item>> listCallback,
				@Nullable Consumer<List<? extends Item>> completionCallback
		);

		default List<? extends Item> getChildren() {
			CompletableFuture<List<? extends Item>> f = getLib().newCompletableFuture();
			getChildren(null, f);
			return f.get(Collections::emptyList);
		}

		default List<? extends Item> getUnsortedChildren() {
			CompletableFuture<List<? extends Item>> f = getLib().newCompletableFuture();
			getChildren(f, null);
			return f.get(Collections::emptyList);
		}

		default List<PlayableItem> getPlayableChildren(boolean recursive) {
			List<? extends Item> children = getUnsortedChildren();
			List<PlayableItem> playable = new ArrayList<>(children.size());
			for (Item c : children) {
				if (c instanceof PlayableItem) playable.add((PlayableItem) c);
				else if (recursive) playable.addAll(((BrowsableItem) c).getPlayableChildren(true));
			}
			return playable;
		}

		Iterator<PlayableItem> getShuffleIterator();

		@Override
		default int getIcon() {
			return R.drawable.folder;
		}

		default void getQueue(Consumer<List<MediaSessionCompat.QueueItem>> consumer) {
			getChildren(null, children -> {
				List<MediaSessionCompat.QueueItem> queue = new ArrayList<>(children.size());
				int id = 0;
				for (Item i : children) {
					queue.add(new MediaSessionCompat.QueueItem(i.getMediaDescription(), id++));
				}
				consumer.accept(queue);
			});
		}

		default PlayableItem getFirstPlayable() {
			for (Item i : getChildren()) {
				if (i instanceof PlayableItem) {
					return (PlayableItem) i;
				} else if (i instanceof BrowsableItem) {
					PlayableItem pi = ((BrowsableItem) i).getFirstPlayable();
					if (pi != null) return pi;
				}
			}

			return null;
		}

		default void search(Pattern query, List<MediaItem> result) {
			for (Item i : getChildren()) {
				if (query.matcher(i.getTitle()).find()) {
					result.add(i.asMediaItem());
				} else if (!(i instanceof BrowsableItem) && (query.matcher(i.getSubtitle()).find())) {
					result.add(i.asMediaItem());
				}

				if (i instanceof BrowsableItem) {
					((BrowsableItem) i).search(query, result);
				}
			}
		}

		@Nullable
		default PlayableItem getLastPlayedItem() {
			String id = getPrefs().getLastPlayedItemPref();
			if (id != null) {
				for (Item i : getUnsortedChildren()) {
					if ((i instanceof PlayableItem) && id.equals(i.getId())) return (PlayableItem) i;
				}
			}
			return null;
		}

		default void updateSorting() {
		}
	}

	interface Folders extends BrowsableItem {

		boolean isFoldersItemId(String id);

		@Override
		default int getIcon() {
			return R.drawable.folder;
		}

		void addItem(Uri uri);

		void removeItem(int idx);

		void removeItem(Item item);

		void moveItem(int fromPosition, int toPosition);
	}

	interface Favorites extends BrowsableItem {

		boolean isFavoriteItem(PlayableItem i);

		boolean isFavoriteItemId(String id);

		void addItem(PlayableItem i);

		void addItems(List<PlayableItem> items);

		void removeItem(int idx);

		void removeItem(PlayableItem i);

		void removeItems(List<PlayableItem> items);

		void moveItem(int fromPosition, int toPosition);

		@Override
		default int getIcon() {
			return R.drawable.favorite_filled;
		}
	}

	interface Playlists extends BrowsableItem {

		boolean isPlaylistsItemId(String id);

		Playlist addItem(CharSequence name);

		void removeItem(int idx);

		void moveItem(int fromPosition, int toPosition);

		@Override
		default int getIcon() {
			return R.drawable.playlist;
		}
	}

	interface Playlist extends BrowsableItem {

		void addItems(List<PlayableItem> items);

		void removeItem(int idx);

		void removeItems(List<PlayableItem> items);

		void moveItem(int fromPosition, int toPosition);

		@Override
		default int getIcon() {
			return R.drawable.playlist;
		}
	}
}
