package me.aap.fermata.media.lib;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.Utils;
import me.aap.utils.text.SharedTextBuilder;

import static me.aap.fermata.BuildConfig.DEBUG;

/**
 * @author Andrey Pavlenko
 */
@SuppressLint("InlinedApi")
class FileItem extends PlayableItemBase {
	public static final String SCHEME = "file";
	private final boolean isVideo;

	private FileItem(String id, BrowsableItem parent, MediaFile file, boolean isVideo) {
		super(id, parent, file);
		this.isVideo = isVideo;
	}

	static FileItem create(String id, BrowsableItem parent, MediaFile file, DefaultMediaLib lib,
												 boolean isFile) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				FileItem f = (FileItem) i;
				if (DEBUG && !parent.equals(f.getParent())) throw new AssertionError();
				if (DEBUG && !file.equals(f.getFile())) throw new AssertionError();
				return f;
			} else {
				return new FileItem(id, parent, file, isFile);
			}
		}
	}


	static FileItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int idx = id.lastIndexOf('/');
		if ((idx == -1) || (idx == (id.length() - 1))) return null;

		String name = id.substring(idx + 1);
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(FolderItem.SCHEME).append(id, SCHEME.length(), idx);
		FolderItem parent = (FolderItem) lib.getItem(tb.releaseString());
		if (parent == null) return null;

		MediaFile file = parent.getFile().getChild(name);
		return (file != null) ? create(id, parent, file, lib, Utils.isVideoFile(file.getName())) : null;
	}

	@Override
	public boolean isVideo() {
		return isVideo;
	}

	@Override
	public Uri getLocation() {
		return getFile().getUri();
	}

	@Override
	public long getDuration() {
		return getMediaData().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
	}

	@Override
	public void setDuration(long duration) {
		MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder(getMediaData());
		b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
		clearCache();
		mediaData = b.build();
	}

	@Override
	public FileItem export(String exportId, BrowsableItem parent) {
		FileItem f = create(exportId, parent, getFile(), (DefaultMediaLib) parent.getLib(), isVideo());
		if (f.mediaData == null) f.mediaData = this.mediaData;
		return f;
	}

	@Override
	public String getOrigId() {
		String id = getId();
		if (id.startsWith(SCHEME)) return id;
		return id.substring(id.indexOf(SCHEME));
	}

	@Override
	MediaMetadataCompat.Builder getMediaMetadataBuilder() {
		MediaMetadataCompat.Builder meta = super.getMediaMetadataBuilder();
		getLib().getMetadataRetriever().getMediaMetadata(meta, this);
		return meta;
	}
}
