package me.aap.fermata.media.lib;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.fermata.util.Utils.isVideoMimeType;
import static me.aap.utils.async.Completed.completedNull;
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

	M3uTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file, String name,
							 String album, String artist, String genre, String logo, long duration, byte type) {
		super(id, parent, file);

		if (type == 1) {
			isVideo = false;
		} else if (type == 2) {
			isVideo = true;
		} else {
			String ext = getFileExtension(file.getRid().getPath());
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
		super(id, parent, i.getResource());
		this.trackNumber = i.trackNumber;
		this.name = i.name;
		this.album = i.album;
		this.artist = i.artist;
		this.genre = i.genre;
		this.logo = i.logo;
		this.duration = i.duration;
		this.isVideo = i.isVideo;
		setMeta(i.getMediaData());
	}

	@NonNull
	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int tid = Integer.parseInt(id.substring(start, end));
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uItem.SCHEME).append(id, end, id.length());

		return lib.getItem(tb.releaseString()).then(i -> {
			M3uItem m3u = (M3uItem) i;
			return (m3u != null) ? m3u.getTrack(gid, tid) : completedNull();
		});
	}

	private M3uItem getM3uItem() {
		Item p = getParent();
		return (p instanceof M3uItem) ? (M3uItem) p : ((M3uGroupItem) p).getParent();
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
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		if (getResource().isLocalFile()) return super.loadMeta();
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
		if (album != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
		if (artist != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
		if (genre != null) meta.putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre);

		if (logo != null) {
			return getM3uItem().getResource().getParent().then(dir ->
					getLib().getVfsManager().resolve(logo, dir).then(f -> {
						if (f != null) meta.setImageUri(f.getRid().toString());
						return super.buildMeta(meta);
					})
			);
		}

		return super.buildMeta(meta);
	}

	@NonNull
	@Override
	public M3uTrackItem export(String exportId, BrowsableItem parent) {
		DefaultMediaLib lib = (DefaultMediaLib) getLib();

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
