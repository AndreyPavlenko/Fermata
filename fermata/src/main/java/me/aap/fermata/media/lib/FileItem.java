package me.aap.fermata.media.lib;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.Utils;
import me.aap.utils.text.TextUtils;

import static me.aap.fermata.BuildConfig.DEBUG;

/**
 * @author Andrey Pavlenko
 */
@SuppressLint("InlinedApi")
class FileItem extends PlayableItemBase {
	static final String albumArtStrUri = "content://media/external/audio/albumart";
	private static final Uri albumArtUri = Uri.parse(albumArtStrUri);
	public static final String SCHEME = "file";
	private static final String[] QUERY;
	private final boolean isVideo;

	static {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			QUERY = new String[]{
					MediaStore.Audio.Media.DURATION,
					MediaStore.Audio.Media.TITLE,
					MediaStore.Audio.Media.ARTIST,
					MediaStore.Audio.Media.COMPOSER,
					MediaStore.Audio.Media.ALBUM,
					MediaStore.Audio.Albums.ALBUM_ID
			};
		} else {
			QUERY = null;
		}
	}

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
		StringBuilder sb = TextUtils.getSharedStringBuilder();
		sb.append(FolderItem.SCHEME).append(id, SCHEME.length(), idx);
		FolderItem parent = (FolderItem) lib.getItem(sb);
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
		return buildMediaMetadata(meta, this);
	}

	static MediaMetadataCompat.Builder buildMediaMetadata(MediaMetadataCompat.Builder meta,
																												PlayableItem item) {
		if (queryMetadata(meta, item)) return meta;

		MediaEngineManager mgr = MediaEngineManager.getInstance();
		if (mgr != null) mgr.getMediaMetadata(meta, item);
		return meta;
	}

	@SuppressWarnings("deprecation")
	private static boolean queryMetadata(MediaMetadataCompat.Builder meta, PlayableItem item) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;

		Uri uri = null;
		String selection = null;
		String[] selectionArgs = null;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			uri = item.getFile().getAudioUri();
		}

		if (uri == null) {
			String path = item.getFile().getPath();

			if (path != null) {
				uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				selection = MediaStore.Audio.Media.DATA + " = ?";
				selectionArgs = new String[]{path};
			}
		}

		if (uri == null) return false;

		ContentResolver cr = FermataApplication.get().getContentResolver();
		try (Cursor c = cr.query(uri, QUERY, selection, selectionArgs, null)) {
			if ((c == null) || !c.moveToNext()) return false;

			long dur = c.getLong(0);
			if (dur <= 0) return false;
			meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur);

			String m = c.getString(1);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);
			else meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getFile().getName());

			m = c.getString(2);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, m);

			m = c.getString(3);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, m);

			m = c.getString(4);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, m);

			MediaLib lib = item.getLib();
			Uri u = ContentUris.withAppendedId(albumArtUri, c.getLong(5));
			m = u.toString();
			Bitmap bm = lib.getCachedBitmap(m);

			if (bm != null) {
				meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bm);
			} else if (isBitmapUri(lib.getContext(), m, u)) {
				meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, m);
			}

			return true;
		} catch (Exception ex) {
			Log.d(FileItem.class.getName(), "Failed to query media metadata", ex);
		}

		return false;
	}

	@SuppressWarnings("unused")
	private static boolean isBitmapUri(Context ctx, String u, Uri uri) {
		try (ParcelFileDescriptor fd = ctx.getContentResolver().openFileDescriptor(uri, "r")) {
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
}
