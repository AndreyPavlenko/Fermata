package me.aap.fermata.media.lib;

import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class PlayableItemWrapper implements PlayableItem {
	private final PlayableItem item;

	public PlayableItemWrapper(PlayableItem item) {
		this.item = item;
	}

	public PlayableItem getItem() {
		return item;
	}

	@Override
	@NonNull
	public String getId() {
		return getItem().getId();
	}

	@Override
	public VirtualResource getResource() {
		return getItem().getResource();
	}

	@Override
	@NonNull
	public String getName() {
		return getItem().getName();
	}

	@Override
	@NonNull
	public MediaLib getLib() {
		return getItem().getLib();
	}

	@Override
	@NonNull
	public MediaLib.BrowsableItem getRoot() {
		return getItem().getRoot();
	}

	@Override
	@NonNull
	public FutureSupplier<MediaDescriptionCompat> getMediaDescription() {
		return getItem().getMediaDescription();
	}

	@Override
	public boolean isExternal() {
		return getItem().isExternal();
	}

	@Override
	public FutureSupplier<MediaDescriptionCompat> getMediaItemDescription() {
		return getItem().getMediaItemDescription();
	}

	@Override
	@NonNull
	public FutureSupplier<Long> getQueueId() {
		return getItem().getQueueId();
	}

	@Override
	@NonNull
	public FutureSupplier<MediaBrowserCompat.MediaItem> asMediaItem() {
		return getItem().asMediaItem();
	}

	@Override
	@NonNull
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		return getItem().getPrevPlayable();
	}

	@Override
	@NonNull
	public FutureSupplier<PlayableItem> getNextPlayable() {
		return getItem().getNextPlayable();
	}

	@Override
	@NonNull
	public FutureSupplier<PlayableItem> getPlayable(boolean next) {
		return getItem().getPlayable(next);
	}

	@Override
	@NonNull
	public FutureSupplier<Void> updateTitles() {
		return getItem().updateTitles();
	}

	@Override
	public boolean addChangeListener(ChangeListener l) {
		return getItem().addChangeListener(l);
	}

	@Override
	public boolean removeChangeListener(ChangeListener l) {
		return getItem().removeChangeListener(l);
	}

	@Override
	@NonNull
	public PlayableItemPrefs getPrefs() {
		return getItem().getPrefs();
	}

	@Override
	public boolean isVideo() {
		return getItem().isVideo();
	}

	@Override
	public boolean isSeekable() {
		return getItem().isSeekable();
	}

	@Override
	@NonNull
	public FutureSupplier<MediaMetadataCompat> getMediaData() {
		return getItem().getMediaData();
	}

	@Override
	@NonNull
	public MediaLib.BrowsableItem getParent() {
		return getItem().getParent();
	}

	@Override
	@NonNull
	public PlayableItem export(String exportId, MediaLib.BrowsableItem parent) {
		return getItem().export(exportId, parent);
	}

	@Override
	public String getOrigId() {
		return getItem().getOrigId();
	}

	@Override
	@NonNull
	public Uri getLocation() {
		return getItem().getLocation();
	}

	@Override
	public boolean isNetResource() {
		return getItem().isNetResource();
	}

	@Override
	public boolean isStream() {
		return getItem().isStream();
	}

	@Override
	@NonNull
	public FutureSupplier<Long> getDuration() {
		return getItem().getDuration();
	}

	@Override
	@NonNull
	public FutureSupplier<Void> setDuration(long duration) {
		return getItem().setDuration(duration);
	}

	@Override
	public boolean isTimerRequired() {
		return getItem().isTimerRequired();
	}

	@Override
	@DrawableRes
	public int getIcon() {
		return getItem().getIcon();
	}

	@Override
	@NonNull
	public FutureSupplier<Uri> getIconUri() {
		return getItem().getIconUri();
	}

	@Override
	public long getOffset() {
		return getItem().getOffset();
	}

	@Override
	public boolean isFavoriteItem() {
		return getItem().isFavoriteItem();
	}

	@Override
	public boolean isRepeatItemEnabled() {
		return getItem().isRepeatItemEnabled();
	}

	@Override
	public void setRepeatItemEnabled(boolean enabled) {
		getItem().setRepeatItemEnabled(enabled);
	}

	@Override
	public boolean isLastPlayed() {
		return getItem().isLastPlayed();
	}

	@Override
	@Nullable
	public String getUserAgent() {
		return getItem().getUserAgent();
	}

	@Override
	public int hashCode() {
		return getItem().hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return getItem().equals(obj);
	}

	@NonNull
	@Override
	public String toString() {
		return getItem().toString();
	}
}
