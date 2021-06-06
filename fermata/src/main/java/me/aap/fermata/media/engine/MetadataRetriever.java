package me.aap.fermata.media.engine;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.content.ContentFileSystem;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_DEFAULT;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_SYSTEM;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_VLC;
import static me.aap.utils.async.Completed.completedEmptyMap;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

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
	private static final String COL_ID_PATTERN = COL_ID + " LIKE ? AND NOT " + COL_ID + " LIKE ?";
	private static final String[] QUERY_COLUMNS = {COL_ID, COL_TITLE, COL_ALBUM, COL_ARTIST,
			COL_DURATION, COL_ART};
	private static final String[] CONTENT_COLUMNS = {
			MediaStore.MediaColumns.TITLE,
			MediaStore.Audio.AudioColumns.DURATION,
			MediaStore.Audio.AudioColumns.ARTIST,
			"album_artist",
			MediaStore.Audio.AudioColumns.ALBUM,
			MediaStore.Audio.AudioColumns.COMPOSER,
			"genre"};

	private final MediaEngineManager mgr;
	private final BitmapCache bitmapCache;
	@Nullable
	private final SQLiteDatabase db;
	private final PromiseQueue queue = new PromiseQueue(App.get().getExecutor());

	public MetadataRetriever(MediaEngineManager mgr) {
		this.mgr = mgr;
		bitmapCache = new BitmapCache(mgr.lib);
		Context ctx = mgr.lib.getContext();
		File cache = ctx.getExternalCacheDir();
		if (cache == null) cache = ctx.getCacheDir();
		File dbFile = new File(cache, "metadata.db");
		SQLiteDatabase db = null;

		try {
			db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
		} catch (Exception ex) {
			Log.w("Failed to create database: ", dbFile, ": ", ex, ". Retrying ...");

			try {
				db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
			} catch (Exception ex1) {
				Log.e(ex1, "Failed to create database: ", dbFile);
			}
		}

		this.db = db;
		createTable();
	}

	public BitmapCache getBitmapCache() {
		return bitmapCache;
	}

	@Override
	public void close() {
		if (db != null) db.close();
	}

	public FutureSupplier<MetadataBuilder> getMediaMetadata(PlayableItem item) {
		return queue.enqueue(() -> load(item));
	}

	private MetadataBuilder load(PlayableItem item) {
		MetadataBuilder meta = queryMetadata(item);
		if (meta != null) return meta;

		MetaBuilder mb = new MetaBuilder();
		VirtualResource file = item.getResource();

		if (file.getVirtualFileSystem() instanceof ContentFileSystem) {
			if (queryContentProvider(file.getRid().toAndroidUri(), mb)) {
				try {
					insertMetadata(mb, item);
				} catch (Throwable ex) {
					Log.e(ex, "Failed to update MediaStore");
				}

				return mb;
			}
		}

		int scanner = (mgr.vlcPlayer == null) ? MEDIA_SCANNER_DEFAULT
				: item.getLib().getPrefs().getMediaScannerPref();

		switch (scanner) {
			case MEDIA_SCANNER_DEFAULT:
			case MEDIA_SCANNER_SYSTEM:
				if (mgr.mediaPlayer.getMediaMetadata(mb, item)) break;
				if ((scanner == MEDIA_SCANNER_SYSTEM) && mgr.mediaPlayer.getDuration(mb, item)) break;
			case MEDIA_SCANNER_VLC:
				if (mgr.vlcPlayer != null) mgr.vlcPlayer.getMediaMetadata(mb, item);
		}

		try {
			insertMetadata(mb, item);
		} catch (Throwable ex) {
			Log.e(ex, "Failed to update MediaStore");
		}

		return mb;
	}

	private boolean queryContentProvider(Uri uri, MetaBuilder mb) {
		App app = App.get();
		ContentResolver cr = app.getContentResolver();

		try (Cursor c = cr.query(uri, CONTENT_COLUMNS, null, null, null)) {
			if ((c == null) || !c.moveToFirst()) return false;

			String m = c.getString(1);

			if ((m != null) && !m.isEmpty()) {
				try {
					mb.putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(m));
				} catch (NumberFormatException ex) {
					Log.d(ex);
					return false;
				}
			} else {
				return false;
			}

			m = c.getString(0);
			if (m != null) mb.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);
			m = c.getString(2);
			if (m != null) mb.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, m);
			m = c.getString(3);
			if (m != null) mb.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, m);
			m = c.getString(4);
			if (m != null) mb.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, m);
			m = c.getString(5);
			if (m != null) mb.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, m);
			m = c.getString(6);
			if (m != null) mb.putString(MediaMetadataCompat.METADATA_KEY_GENRE, m);

			mb.setImageUri(uri.toString());
			return true;
		} catch (Exception ex) {
			Log.e(ex, "Failed to query content provider: " + uri);
			return false;
		}
	}

	public FutureSupplier<Map<String, MetadataBuilder>> queryMetadata(String idPattern) {
		return (db != null) ? queue.enqueue(() -> query(idPattern)) : completedEmptyMap();
	}

	public FutureSupplier<String> queryId(String pattern) {
		String[] p = {'%' + pattern + '%'};
		return (db != null) ? queue.enqueue(() -> {
			try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_TITLE + " LIKE ?  LIMIT 1",
					p, null, null, null)) {
				if (c.moveToFirst()) return c.getString(0);
			}
			try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_ARTIST + " LIKE ?  LIMIT 1",
					p, null, null, null)) {
				if (c.moveToFirst()) return c.getString(0);
			}
			try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_ALBUM + " LIKE ?  LIMIT 1",
					p, null, null, null)) {
				if (c.moveToFirst()) return c.getString(0);
			}

			return null;
		}) : completedNull();
	}

	public FutureSupplier<Void> clearMetadata(String idPattern) {
		return (db != null) ? queue.enqueue(() -> clear(idPattern)) : completedVoid();
	}

	public FutureSupplier<Void> updateDuration(PlayableItem item, long duration) {
		if (db == null) return completedVoid();

		return queue.enqueue(() -> {
			ContentValues values = new ContentValues(1);
			values.put(COL_DURATION, duration);
			db.update(TABLE, values, COL_ID + " = ?", new String[]{item.getId()});
			return null;
		});
	}

	private Map<String, MetadataBuilder> query(String idPattern) {
		assert db != null;

		try (SharedTextBuilder tb = SharedTextBuilder.get().append(idPattern).append("%/%");
				 Cursor c = db.query(TABLE, QUERY_COLUMNS, COL_ID_PATTERN,
						 new String[]{idPattern, tb.toString()}, null, null, null)) {
			int count = c.getCount();
			if (count == 0) return Collections.emptyMap();

			Map<String, MetadataBuilder> result = new HashMap<>((int) (count * 1.5f));

			while (c.moveToNext()) {
				String id = c.getString(0);
				MetadataBuilder meta = new MetadataBuilder();
				readMetadata(meta, c, tb);
				result.put(id, meta);
			}

			return result;
		} catch (Throwable ex) {
			Log.d(ex, "Failed to query media metadata");
			return Collections.emptyMap();
		}
	}

	private Void clear(String idPattern) {
		try {
			String not = SharedTextBuilder.get().append(idPattern).append("%/%").releaseString();
			assert db != null;
			db.delete(TABLE, COL_ID_PATTERN, new String[]{idPattern, not});
		} catch (Throwable ex) {
			Log.d(ex, "Failed to clear media metadata");
		}
		return null;
	}

	private MetadataBuilder queryMetadata(PlayableItem item) {
		if (db == null) return null;

		try (Cursor c = db.query(TABLE, QUERY_COLUMNS, COL_ID + " = ?",
				new String[]{item.getOrigId()}, null, null, null);
				 SharedTextBuilder tb = SharedTextBuilder.get()) {
			if (!c.moveToNext()) return null;
			MetadataBuilder meta = new MetaBuilder();
			readMetadata(meta, c, tb);
			return meta;
		} catch (Throwable ex) {
			Log.d(ex, "Failed to query media metadata");
			return null;
		}
	}

	private void readMetadata(MetadataBuilder meta, Cursor c, TextBuilder tb) {
		String m = c.getString(1);
		if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);

		m = c.getString(2);
		if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, m.intern());

		m = c.getString(3);
		if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, m.intern());

		meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, c.getLong(4));

		byte[] art = c.getBlob(5);
		if (art != null) meta.setImageUri(bitmapCache.getImageUri(art, tb));
	}

	private void insertMetadata(MetaBuilder meta, PlayableItem item) {
		if ((db == null) || !meta.durationSet) return;
		ContentValues values = meta.values;
		Bitmap bm = meta.image;

		if (bm == null) {
			String u = meta.getImageUri();
			if (u != null) bm = item.getLib().getBitmap(u, false, false).get(null);
		}

		if (bm != null) {
			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				byte[] art = bitmapCache.saveBitmap(bm, tb);

				if (art != null) {
					meta.setImageUri(bitmapCache.getImageUri(art, tb));
					values.put(COL_ART, art);
				}
			}
		}

		values.put(COL_ID, item.getId());
		db.insert(TABLE, null, values);
	}

	private void createTable() {
		if (db == null) return;

		PreferenceStore ps = FermataApplication.get().getPreferenceStore();
		Pref<IntSupplier> version = Pref.i("METADATA_VERSION", 0);

		if (ps.getIntPref(version) < 70) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE);
			ps.applyIntPref(version, BuildConfig.VERSION_CODE);
		}

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

	private static final class MetaBuilder extends MetadataBuilder {
		final ContentValues values = new ContentValues(10);
		boolean durationSet;
		Bitmap image;

		@Override
		public void putString(String k, String v) {
			switch (k) {
				case MediaMetadataCompat.METADATA_KEY_TITLE:
					values.put(COL_TITLE, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_ALBUM:
					values.put(COL_ALBUM, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_ARTIST:
					values.put(COL_ARTIST, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST:
					values.put(COL_ALBUM_ARTIST, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_COMPOSER:
					values.put(COL_COMPOSER, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_WRITER:
					values.put(COL_WRITER, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_GENRE:
					values.put(COL_GENRE, v);
					break;
				case MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI:
					setImageUri(v);
					return;
			}

			super.putString(k, v);
		}

		@Override
		public void putLong(String key, long value) {
			if (MediaMetadata.METADATA_KEY_DURATION.equals(key)) {
				values.put(COL_DURATION, value);
				durationSet = true;
			}

			super.putLong(key, value);
		}

		@Override
		public void putBitmap(String key, Bitmap value) {
			if (MediaMetadata.METADATA_KEY_ALBUM_ART.equals(key)) image = value;
			else super.putBitmap(key, value);
		}
	}
}
