package me.aap.fermata.media.lib;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.fermata.BuildConfig.DEBUG;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
class CueTrackItem extends PlayableItemBase {
	public static final String SCHEME = "cuetrack";
	private final int trackNumber;
	private final long offset;
	private final boolean isVideo;
	private MediaMetadataCompat.Builder metaBuilder;

	CueTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file, String title,
							 String performer, String writer, String albumTitle, long offset, boolean isVideo) {
		super(id, parent, file);

		MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder();
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);

		if (performer != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, performer);
		}
		if (writer != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_WRITER, writer);
		}
		if (albumTitle != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumTitle);
		}

		this.trackNumber = trackNumber;
		this.offset = offset;
		this.isVideo = isVideo;
	}

	private CueTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file,
											 long offset, boolean isVideo) {
		super(id, parent, file);
		this.trackNumber = trackNumber;
		this.offset = offset;
		this.isVideo = isVideo;
	}

	@NonNull
	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int i1 = id.indexOf(':');
		if (i1 == -1) return completedNull();
		int i2 = id.indexOf(':', i1 + 1);
		if (i2 == -1) return completedNull();

		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(CueItem.SCHEME).append(id, i2, id.length());

		return lib.getItem(tb.releaseString()).map(i -> {
			CueItem cue = (CueItem) i;
			if (cue == null) return null;

			int n = Integer.parseInt(id.substring(i1 + 1, i2));
			return cue.getTrack(n);
		});
	}

	public int getTrackNumber() {
		return trackNumber;
	}

	@Override
	public boolean isVideo() {
		return isVideo;
	}

	@NonNull
	@Override
	FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		return completed(meta.build());
	}

	@Override
	public long getOffset() {
		return offset;
	}

	void build(long duration) {
		metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
		setMeta(metaBuilder.build());
		metaBuilder = null;
	}

	@NonNull
	@Override
	public FutureSupplier<Void> setDuration(long duration) {
		return completedVoid();
	}

	@Override
	public boolean isTimerRequired() {
		return true;
	}

	@NonNull
	@Override
	public CueTrackItem export(String exportId, BrowsableItem parent) {
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();
		CueTrackItem exported;

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(exportId);

			if (i != null) {
				CueTrackItem c = (CueTrackItem) i;
				if (DEBUG && !parent.equals(c.getParent())) throw new AssertionError();
				if (DEBUG && !getFile().equals(c.getFile())) throw new AssertionError();
				return c;
			} else {
				exported = new CueTrackItem(exportId, parent, trackNumber, getFile(), offset, isVideo);
			}
		}

		exported.setMeta(getMediaData());
		return exported;
	}

	@Override
	public String getOrigId() {
		String id = getId();
		if (id.startsWith(SCHEME)) return id;
		return id.substring(id.indexOf(SCHEME));
	}
}
