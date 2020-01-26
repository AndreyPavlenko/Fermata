package me.aap.fermata.media.lib;

import android.content.Context;
import android.media.session.MediaSession;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.function.Consumer;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.NaturalOrderComparator;

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
	Item getItem(CharSequence id);

	@Nullable
	PlayableItem getLastPlayedItem();

	long getLastPlayedPosition(PlayableItem i);

	void setLastPlayed(PlayableItem i, long position);

	void getChildren(String parentMediaId, MediaBrowserServiceCompat.Result<List<MediaItem>> result);

	void search(String query, MediaBrowserServiceCompat.Result<List<MediaItem>> result);

	default void clearCache() {
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

		MediaItem asMediaItem();

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

		default MediaDescriptionCompat getMediaDescription() {
			return getMediaDescriptionBuilder().build();
		}

		default MediaDescriptionCompat.Builder getMediaDescriptionBuilder() {
			return new MediaDescriptionCompat.Builder().setMediaId(getId())
					.setTitle(getTitle()).setSubtitle(getSubtitle());
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

		boolean isMediaDataLoaded();

		MediaMetadataCompat getMediaData();

		Future<Void> getMediaData(Consumer<MediaMetadataCompat> consumer);

		@NonNull
		@Override
		BrowsableItem getParent();

		Uri getLocation();

		PlayableItem export(String exportId, BrowsableItem parent);

		String getOrigId();

		long getDuration();

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

		@Override
		default MediaItem asMediaItem() {
			return new MediaItem(getMediaDescription(), MediaItem.FLAG_PLAYABLE);
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

		List<? extends Item> getChildren();

		List<? extends Item> getChildren(@Nullable Consumer<List<? extends Item>> onLoadingCompletion);

		default List<PlayableItem> getPlayableChildren(boolean recursive) {
			List<? extends Item> children = getChildren(null);
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

		@Override
		default MediaItem asMediaItem() {
			return new MediaItem(getMediaDescription(), MediaItem.FLAG_BROWSABLE);
		}

		default List<MediaSessionCompat.QueueItem> getQueue() {
			List<? extends Item> children = getChildren();
			List<MediaSessionCompat.QueueItem> queue = new ArrayList<>(children.size());
			int id = 0;

			for (Item i : children) {
				queue.add(new MediaSessionCompat.QueueItem(i.getMediaDescription(), id++));
			}

			return queue;
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
				for (Item i : getChildren(null)) {
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
