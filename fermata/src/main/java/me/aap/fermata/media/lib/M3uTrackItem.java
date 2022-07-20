package me.aap.fermata.media.lib;

import static me.aap.fermata.util.Utils.isVideoMimeType;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.io.FileUtils.getFileExtension;
import static me.aap.utils.io.FileUtils.getMimeTypeFromExtension;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class M3uTrackItem extends PlayableItemBase {
	static final byte TYPE_UNKNOWN = 0;
	static final byte TYPE_AUDIO = 1;
	static final byte TYPE_VIDEO = 2;
	static final byte TYPE_STREAM = 4;
	public static final String SCHEME = "m3ut";
	private final String name;
	private final String album;
	private final String artist;
	private final String genre;
	private final int trackNumber;
	private final String logo;
	private final String tvgId;
	private final String tvgName;
	private final long duration;
	private final byte type;

	protected M3uTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file,
												 String name, String album, String artist, String genre, String logo,
												 String tvgId, String tvgName, long duration, byte type) {
		super(id, parent, file);
		String ext = getFileExtension(file.getRid().getPath());

		if (type == TYPE_UNKNOWN) {
			if ((ext == null) || ext.startsWith("m3u")) type = TYPE_VIDEO | TYPE_STREAM;
			else if (isVideoMimeType(getMimeTypeFromExtension(ext))) type = TYPE_VIDEO;
			else type = TYPE_AUDIO;
		} else if ((ext == null) || ext.startsWith("m3u")) {
			type |= TYPE_STREAM;
		}

		this.name = (name != null) && !name.isEmpty() ? name : file.getName();
		this.album = album;
		this.artist = artist;
		this.genre = genre;
		this.logo = logo;
		this.tvgId = tvgId;
		this.tvgName = tvgName;
		this.trackNumber = trackNumber;
		this.duration = duration;
		this.type = type;
	}

	@NonNull
	static FutureSupplier<? extends M3uTrackItem> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int tid = Integer.parseInt(id.substring(start, end));
		start = id.indexOf(':', end + 1);
		String uri = (start > 0) ? id.substring(start + 1) : null;
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uItem.SCHEME).append(id, end, (start > 0) ? start : id.length());

		return lib.getItem(tb.releaseString()).then(i -> {
			M3uItem m3u = (M3uItem) i;
			return (m3u != null) ? m3u.getTrack(gid, tid, uri) : completedNull();
		});
	}

	protected M3uItem getM3uItem() {
		Item p = getParent();
		return (p instanceof M3uItem) ? (M3uItem) p : ((M3uGroupItem) p).getParent();
	}

	public String getScheme() {
		return SCHEME;
	}

	public int getTrackNumber() {
		return trackNumber;
	}

	@NonNull
	public String getName() {
		String n = name;
		return (n != null) ? n : getResource().getName();
	}

	public String getTvgId() {
		return tvgId;
	}

	public String getTvgName() {
		return tvgName;
	}

	@Override
	public boolean isVideo() {
		return (type & TYPE_VIDEO) != 0;
	}

	@Override
	public boolean isStream() {
		return (type & TYPE_STREAM) != 0;
	}

	protected String getLogo() {
		return logo;
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
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getName());
		if (album != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
		if (artist != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
		if (genre != null) meta.putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre);
		if (duration > 0) meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration * 1000);

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

	@Override
	public String getOrigId() {
		String s = getScheme();
		String id = getId();
		if (id.startsWith(s)) return id;
		return id.substring(id.indexOf(s));
	}

	@Nullable
	@Override
	public String getUserAgent() {
		VirtualResource r = getM3uItem().getResource();
		return (r instanceof M3uFile) ? ((M3uFile) r).getUserAgent() : null;
	}
}
