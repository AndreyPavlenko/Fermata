package me.aap.fermata.media.lib;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.text.TextUtils;

import static me.aap.fermata.BuildConfig.DEBUG;

/**
 * @author Andrey Pavlenko
 */
class CueTrackItem extends PlayableItemBase {
	public static final String SCHEME = "cuetrack";
	private final int trackNumber;
	private final String name;
	private final String performer;
	private final String writer;
	private final String albumTitle;
	private final long offset;
	private final boolean isVideo;
	private long duration;

	CueTrackItem(String id, BrowsableItem parent, int trackNumber, MediaFile file, String title,
							 String performer, String writer, String albumTitle, long offset, boolean isVideo) {
		super(id, parent, file);
		this.trackNumber = trackNumber;
		this.name = title;
		this.performer = performer;
		this.writer = writer;
		this.albumTitle = albumTitle;
		this.offset = offset;
		this.isVideo = isVideo;
	}

	static CueTrackItem create(String id, BrowsableItem parent, int trackNumber, MediaFile file,
														 String title, String performer, String writer, String albumTitle,
														 long offset, boolean isVideo) {
		Item i = ((DefaultMediaLib) parent.getLib()).getFromCache(id);

		if (i != null) {
			CueTrackItem c = (CueTrackItem) i;
			if (DEBUG && !parent.equals(c.getParent())) throw new AssertionError();
			if (DEBUG && !file.equals(c.getFile())) throw new AssertionError();
			return c;
		} else {
			return new CueTrackItem(id, parent, trackNumber, file, title, performer, writer,
					albumTitle, offset, isVideo);
		}
	}

	static CueTrackItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int i1 = id.indexOf(':');
		if (i1 == -1) return null;
		int i2 = id.indexOf(':', i1 + 1);
		if (i2 == -1) return null;

		StringBuilder sb = TextUtils.getSharedStringBuilder();
		sb.append(CueItem.SCHEME).append(id, i2, id.length());
		CueItem cue = (CueItem) lib.getItem(sb);
		if (cue == null) return null;

		i1 = Integer.parseInt(id.substring(i1 + 1, i2));
		return cue.getTrack(i1);
	}

	@Override
	public String getName() {
		return name;
	}

	public int getTrackNumber() {
		return trackNumber;
	}

	@Override
	public boolean isVideo() {
		return isVideo;
	}

	@Override
	public MediaMetadataCompat.Builder getMediaMetadataBuilder() {
		MediaMetadataCompat.Builder meta = super.getMediaMetadataBuilder();
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getName());
		meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());

		if (performer != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, performer);
		}
		if (writer != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_WRITER, writer);
		}
		if (albumTitle != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumTitle);
		}

		return meta;
	}

	@Override
	public Uri getLocation() {
		return getFile().getUri();
	}

	@Override
	public long getOffset() {
		return offset;
	}

	@Override
	public long getDuration() {
		return duration;
	}

	@Override
	public boolean isTimerRequired() {
		return true;
	}

	void setDuration(long duration) {
		this.duration = duration;
	}

	@Override
	public CueTrackItem export(String exportId, BrowsableItem parent) {
		CueTrackItem i = CueTrackItem.create(exportId, parent, trackNumber, getFile(), name,
				performer, writer, albumTitle, offset, isVideo);
		i.setDuration(getDuration());
		return i;
	}

	@Override
	public String getOrigId() {
		String id = getId();
		if (id.startsWith(SCHEME)) return id;
		return id.substring(id.indexOf(SCHEME));
	}

	@Override
	public boolean isMediaDataLoaded() {
		return true;
	}
}
