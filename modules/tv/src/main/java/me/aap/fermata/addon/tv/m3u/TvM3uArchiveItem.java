package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
class TvM3uArchiveItem extends TvM3uEpgItem implements ArchiveItem, PlayableItemPrefs {
	private FutureSupplier<MediaMetadataCompat> md;

	TvM3uArchiveItem(String id, @NonNull TvM3uTrackItem track, long start, long end,
									 String title, String description, String icon) {
		super(id, track, start, end, title, description, icon);
	}

	TvM3uArchiveItem(TvM3uEpgItem i) {
		this(i.getId(), i.getParent(), i.start, i.end, i.title, i.descr, i.icon);
		set(i);
	}

	@NonNull
	@Override
	public PlayableItemPrefs getPrefs() {
		return this;
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<MediaMetadataCompat> getMediaData() {
		FutureSupplier<MediaMetadataCompat> md = this.md;
		if (md != null) return md;
		MetadataBuilder b = new MetadataBuilder();
		b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
		b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, descr);
		b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, end - start);
		if (icon != null) b.setImageUri(icon);
		return this.md = completed(b.build());
	}

	@NonNull
	@Override
	public PlayableItem export(String exportId, MediaLib.BrowsableItem parent) {
		return getParent().export(exportId, parent);
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		TvM3uEpgItem prev = getPrev();
		return (prev instanceof TvM3uArchiveItem) && !((TvM3uArchiveItem) prev).isExpired()
				? completed((TvM3uArchiveItem) prev) : completedNull();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		TvM3uEpgItem next = getNext();
		return (next instanceof TvM3uArchiveItem) && !((TvM3uArchiveItem) next).isExpired()
				? completed((TvM3uArchiveItem) next) : completed(getParent());
	}

	@Override
	public long getExpirationTime() {
		return start + ((24 * 60 * 60000) * getParent().getCatchUpDays());
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	void scheduleReplacement() {
		long delay = getExpirationTime() - System.currentTimeMillis();
		if (delay < 0) return;
		App.get().getHandler().postDelayed(() -> {
			TvM3uTrackItem t = getParent();
			if (t.isArchive(start, end)) return;
			t.replace(this, TvM3uEpgItem::new);
		}, delay);
	}
}
