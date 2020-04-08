package me.aap.fermata.media.engine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class MetadataRetriever implements Closeable {
	private static final String TABLE = "Metadata";
	private static final String COL_ID = "Id";
	private static final String COL_TITLE = "Title";
	private static final String COL_ALBUM = "Album";
	private static final String COL_ARTIST = "Artist";
	private static final String COL_ALBUM_ARTIST = "AlbumArtist";
	private static final String COL_COMPOSER = "Composer";
	private static final String COL_WRITER = "Writer";
	private static final String COL_GENRE = "Genre";
	private static final String COL_ART = "Art";
	private static final String COL_DURATION = "Duration";
	private static final String[] QUERY_COLUMNS = {COL_ID, COL_TITLE, COL_ALBUM, COL_ARTIST,
			COL_DURATION, COL_ART};

	private final MediaEngineManager mgr;
	private final File imageCache;
	private final String imageCacheUri;
	@Nullable
	private final SQLiteDatabase db;

	public MetadataRetriever(MediaEngineManager mgr) {
		this.mgr = mgr;
		Context ctx = mgr.lib.getContext();
		File cache = ctx.getExternalCacheDir();
		File dbFile = new File(cache, "metadata.db");
		SQLiteDatabase db = null;
		imageCache = new File(cache, "images");
		imageCacheUri = Uri.fromFile(imageCache).toString();

		try {
			db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
		} catch (Exception ex) {
			Log.w(getClass().getName(), "Failed to create database: " + dbFile + ": "
					+ ex + ". Retrying ...");

			try {
				db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
			} catch (Exception ex1) {
				Log.e(getClass().getName(), "Failed to create database: " + dbFile, ex1);
			}
		}

		this.db = db;
		createTable();
	}

	@Override
	public void close() {
		if (db != null) db.close();
	}

	public void getMediaMetadata(MediaMetadataCompat.Builder meta, MediaLib.PlayableItem item) {
		if (queryMetadata(meta, item)) return;

		MediaEngineProvider vlcPlayer = mgr.vlcPlayer;

		if ((vlcPlayer != null) && (!"content".equals(item.getLocation().getScheme()))) {
			if (!vlcPlayer.getMediaMetadata(meta, item)) mgr.mediaPlayer.getMediaMetadata(meta, item);
		} else {
			mgr.mediaPlayer.getMediaMetadata(meta, item);
		}

		try {
			insertMetadata(meta, item);
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to update MediaStore", ex);
		}
	}

	public Map<String, MediaMetadataCompat.Builder> queryMetadata(String idPattern) {
		if (db == null) return Collections.emptyMap();

		StringBuilder sb = new StringBuilder(128);
		sb.append(idPattern).append("%/%");

		try (Cursor c = db.query(TABLE, QUERY_COLUMNS, COL_ID + " LIKE ? AND NOT " + COL_ID + " LIKE ?",
				new String[]{idPattern, sb.toString()}, null, null, null)) {
			int count = c.getCount();
			if (count == 0) return Collections.emptyMap();

			Map<String, MediaMetadataCompat.Builder> result = new HashMap<>((int) (count * 1.5f));

			while (c.moveToNext()) {
				String id = c.getString(0);
				MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder();
				readMetadata(meta, c, sb);
				result.put(id, meta);
			}

			return result;
		} catch (Exception ex) {
			Log.d(MetadataRetriever.class.getName(), "Failed to query media metadata", ex);
			return Collections.emptyMap();
		}
	}

	private boolean queryMetadata(MediaMetadataCompat.Builder meta, MediaLib.PlayableItem item) {
		if (db == null) return false;

		try (Cursor c = db.query(TABLE, QUERY_COLUMNS, COL_ID + " = ?",
				new String[]{item.getOrigId()}, null, null, null)) {
			if (!c.moveToNext()) return false;
			StringBuilder sb = new StringBuilder(128);
			readMetadata(meta, c, sb);
			return true;
		} catch (Exception ex) {
			Log.d(MetadataRetriever.class.getName(), "Failed to query media metadata", ex);
			return false;
		}
	}

	private void readMetadata(MediaMetadataCompat.Builder meta, Cursor c, StringBuilder sb) {
		String m = c.getString(1);
		if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);

		m = c.getString(2);
		if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, m.intern());

		m = c.getString(3);
		if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, m.intern());

		meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, c.getLong(4));

		byte[] art = c.getBlob(5);

		if (art != null) {
			meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, getImageUri(art, sb));
		}
	}

	private void insertMetadata(MediaMetadataCompat.Builder meta, MediaLib.PlayableItem item) {
		if (db == null) return;

		MediaMetadataCompat m = meta.build();
		long dur = m.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
		if (dur <= 0) return;

		ContentValues values = new ContentValues(10);
		values.put(COL_ID, item.getOrigId());

		String v = m.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
		if (v != null) values.put(COL_TITLE, v);

		v = m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
		if (v != null) values.put(COL_ALBUM, v);

		v = m.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
		if (v != null) values.put(COL_ARTIST, v);

		v = m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
		if (v != null) values.put(COL_ALBUM_ARTIST, v);

		v = m.getString(MediaMetadataCompat.METADATA_KEY_COMPOSER);
		if (v != null) values.put(COL_COMPOSER, v);

		v = m.getString(MediaMetadataCompat.METADATA_KEY_WRITER);
		if (v != null) values.put(COL_WRITER, v);

		v = m.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
		if (v != null) values.put(COL_GENRE, v);

		values.put(COL_DURATION, dur);

		Bitmap bm = m.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);

		if (bm == null) {
			String u = m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
			if (u != null) bm = item.getLib().getBitmap(u, false, false);
		}

		if (bm != null) {
			StringBuilder sb = new StringBuilder(128);
			byte[] art = saveImage(bm, sb);

			if (art != null) {
				meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
				meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, getImageUri(art, sb));
				values.put(COL_ART, art);
			}
		}

		db.insert(TABLE, null, values);
	}

	@SuppressWarnings("unused")
	private static boolean isBitmapUri(Context ctx, String u, Uri uri) {
		try (ParcelFileDescriptor fd = ctx.getContentResolver().openFileDescriptor(uri, "r")) {
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	private void createTable() {
		if (db == null) return;
		db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
				COL_ID + " VARCHAR NOT NULL UNIQUE, " +
				COL_TITLE + " VARCHAR, " +
				COL_ALBUM + " VARCHAR, " +
				COL_ARTIST + " VARCHAR, " +
				COL_ALBUM_ARTIST + " VARCHAR, " +
				COL_COMPOSER + " VARCHAR, " +
				COL_WRITER + " VARCHAR, " +
				COL_GENRE + " VARCHAR, " +
				COL_DURATION + " INTEGER, " +
				COL_ART + " BLOB " +
				");");
	}

	private String getImageUri(byte[] hash, StringBuilder sb) {
		sb.setLength(0);
		sb.append(imageCacheUri);
		int len = sb.length();
		TextUtils.appendHexString(sb.append("/X/"), hash).append(".jpg");
		sb.setCharAt(len + 1, sb.charAt(len + 3));
		return sb.toString().intern();
	}

	private byte[] saveImage(Bitmap bm, StringBuilder sb) {
		if (bm == null) return null;

		try {
			MemOutputStream mos = new MemOutputStream(bm.getByteCount());
			if (!bm.compress(Bitmap.CompressFormat.JPEG, 100, mos)) return null;

			byte[] content = mos.trimBuffer();
			MessageDigest md = MessageDigest.getInstance("sha-1");
			md.update(content);
			byte[] digest = md.digest();
			sb.setLength(0);
			TextUtils.appendHexString(sb.append("X/"), digest).append(".jpg");
			sb.setCharAt(0, sb.charAt(2));
			File f = new File(imageCache, sb.toString());

			if (!f.isFile()) {
				File dir = f.getParentFile();
				if (dir != null) //noinspection ResultOfMethodCallIgnored
					dir.mkdirs();
				try (OutputStream os = new FileOutputStream(f)) {
					os.write(content);
				}
			}

			return digest;
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to save image", ex);
			return null;
		}
	}
}
