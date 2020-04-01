package me.aap.fermata.media.lib;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.text.TextUtils;

import static me.aap.fermata.util.Utils.isVideoMimeType;
import static me.aap.utils.io.FileUtils.getFileExtension;
import static me.aap.utils.io.FileUtils.getMimeTypeFromExtension;

/**
 * @author Andrey Pavlenko
 */
class M3uTrackItem extends PlayableItemBase {
	public static final String SCHEME = "m3ut";
	private final String name;
	private final String album;
	private final String artist;
	private final String genre;
	private final int trackNumber;
	private final String logo;
	private final boolean isVideo;
	private long duration;

	M3uTrackItem(String id, BrowsableItem parent, int trackNumber, MediaFile file, String name,
							 String album, String artist, String genre, String logo, long duration, byte type) {
		super(id, parent, file);

		if (type == 1) {
			isVideo = false;
		} else if (type == 2) {
			isVideo = true;
		} else {
			String ext = getFileExtension(file.getUri().getPath());
			if (ext == null) isVideo = true;
			else isVideo = ext.startsWith("m3u") || isVideoMimeType(getMimeTypeFromExtension(ext));
		}

		this.name = (name != null) && !name.isEmpty() ? name : file.getName();
		this.album = album;
		this.artist = artist;
		this.genre = genre;
		this.logo = logo;
		this.trackNumber = trackNumber;
		this.duration = file.isLocalFile() ? duration : 0;
	}

	private M3uTrackItem(String id, BrowsableItem parent, M3uTrackItem i) {
		super(id, parent, i.getFile());
		this.trackNumber = i.trackNumber;
		this.name = i.name;
		this.album = i.album;
		this.artist = i.artist;
		this.genre = i.genre;
		this.logo = i.logo;
		this.duration = i.duration;
		this.isVideo = i.isVideo;
		mediaData = i.mediaData;
	}

	static M3uTrackItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int tid = Integer.parseInt(id.substring(start, end));
		StringBuilder sb = TextUtils.getSharedStringBuilder();
		sb.append(M3uItem.SCHEME).append(id, end, id.length());
		M3uItem m3u = (M3uItem) lib.getItem(sb);
		return (m3u != null) ? m3u.getTrack(gid, tid) : null;
	}

	private M3uItem getM3uItem() {
		Item p = getParent();
		return (p instanceof M3uItem) ? (M3uItem) p : ((M3uGroupItem) p).getParent();
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
	MediaMetadataCompat.Builder getMediaMetadataBuilder() {
		M3uItem m3u = getM3uItem();
		MediaMetadataCompat.Builder meta = super.getMediaMetadataBuilder();
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
		if (duration > 0) meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

		if (logo != null) {
			MediaFile m3uFile = m3u.getFile();
			MediaFile dir = Objects.requireNonNull(m3uFile.getParent());
			MediaFile f = MediaFile.resolve(logo, dir);
			if (f != null) {
				meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, f.getUri().toString());
			}
		}

		if (album != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
		if (artist != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
		if (genre != null) meta.putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre);
		if (getFile().isLocalFile()) FileItem.buildMediaMetadata(meta, this);

		return meta;
	}

	@Override
	public Uri getLocation() {
		return getFile().getUri();
	}

	@Override
	public long getDuration() {
		if (duration > 0) return duration;
		duration = getMediaData().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
		return duration;
	}

	@Override
	public void setDuration(long duration) {
		this.duration = duration;
		MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder(getMediaData());
		b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
		clearCache();
		mediaData = b.build();
	}

	@Override
	public M3uTrackItem export(String exportId, BrowsableItem parent) {
		DefaultMediaLib lib = getLib();

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(exportId);
			if (i instanceof M3uTrackItem) return (M3uTrackItem) i;
			return new M3uTrackItem(exportId, parent, this);
		}
	}

	@Override
	public String getOrigId() {
		String id = getId();
		if (id.startsWith(SCHEME)) return id;
		return id.substring(id.indexOf(SCHEME));
	}
}
