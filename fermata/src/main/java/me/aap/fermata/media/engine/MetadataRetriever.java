package me.aap.fermata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_DEFAULT;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_SYSTEM;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_VLC;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.security.SecurityUtils.SHA1_DIGEST_LEN;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.FileItem;
import me.aap.fermata.media.lib.FolderItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
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
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.content.ContentFileSystem;
import me.aap.utils.vfs.content.ContentFolder;
import me.aap.utils.vfs.local.LocalFileSystem;

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
	private static final String[] QUERY_COLUMNS =
			{COL_ID, COL_TITLE, COL_ALBUM, COL_ARTIST, COL_DURATION, COL_ART};
	private static final byte ART_URI = 0;
	private static final byte ART_HASH = 1;
	private static final String[] CONTENT_COLUMNS;
	private static final String[] CONTENT_COLUMNS_DATA;

	static {
		if (SDK_INT >= VERSION_CODES.R) {
			CONTENT_COLUMNS = new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
					MediaStore.Audio.AudioColumns.DURATION, MediaStore.Audio.AudioColumns.ARTIST,
					MediaStore.Audio.AudioColumns.ALBUM, MediaStore.Audio.AudioColumns.ALBUM_ARTIST,
					MediaStore.Audio.AudioColumns.COMPOSER, MediaStore.Audio.AudioColumns.GENRE};
		} else {
			CONTENT_COLUMNS = new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
					MediaStore.Audio.AudioColumns.DURATION, MediaStore.Audio.AudioColumns.ARTIST,
					MediaStore.Audio.AudioColumns.ALBUM};
		}

		CONTENT_COLUMNS_DATA = Arrays.copyOf(CONTENT_COLUMNS, CONTENT_COLUMNS.length + 1);
		CONTENT_COLUMNS_DATA[CONTENT_COLUMNS.length] = "_data";
	}

	private final MediaEngineManager mgr;
	private final BitmapCache bitmapCache;
	@Nullable
	private final SQLiteDatabase db;
	private final PromiseQueue queue = new PromiseQueue(App.get().getExecutor());

	public MetadataRetriever(MediaEngineManager mgr) {
		this.mgr = mgr;
		bitmapCache = FermataApplication.get().getBitmapCache();
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
		VirtualResource res = item.getResource();
		VirtualFileSystem fs = res.getVirtualFileSystem();

		if (fs instanceof LocalFileSystem) {
			try {
				if (queryMediaStore(item, mb)) {
					insertMetadata(mb, item);
					return mb;
				}
			} catch (Throwable ex) {
				Log.e(ex, "Failed retrieve data from MediaStore: ", res);
			}
		} else if ((fs instanceof ContentFileSystem) || "content".equals(res.getRid().getScheme())) {
			try {
				if (queryContentProvider(item, mb)) {
					insertMetadata(mb, item);
					return mb;
				}
			} catch (Throwable ex) {
				Log.e(ex, "Failed retrieve data from MediaStore: ", res);
			}
		}

		int scanner = (mgr.vlcPlayer == null) ? MEDIA_SCANNER_DEFAULT :
				item.getLib().getPrefs().getMediaScannerPref();

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

	private boolean queryContentProvider(PlayableItem item, MetaBuilder mb) {
		ContentResolver cr = App.get().getContentResolver();
		Uri uri = item.getResource().getRid().toAndroidUri();
		Uri mu = toMediaUri(uri);
		if (mu != null) uri = mu;

		try (Cursor c = cr.query(uri, CONTENT_COLUMNS, null, null, null)) {
			if ((c == null) || !c.moveToFirst() || !addFields(c, mb)) return false;
			mb.setImageUri(uri.toString());
			return true;
		}
	}

	private boolean queryMediaStore(PlayableItem item, MetaBuilder mb) {
		String path = item.getResource().getRid().getPath();
		if (path == null) return false;
		String dataQuery;
		String[] dataArgs;
		String canonical = null;
		ContentResolver cr = App.get().getContentResolver();
		boolean video = item.isVideo();
		Uri uri = video ? MediaStore.Video.Media.getContentUri("external") :
				MediaStore.Audio.Media.getContentUri("external");

		try {
			canonical = new File(path).getCanonicalPath();
		} catch (Throwable ignore) {
		}

		if (canonical != null) {
			dataQuery = "_data = ? OR _data = ?";
			dataArgs = new String[]{canonical, path};
		} else {
			dataQuery = "_data = ?";
			dataArgs = new String[]{path};
		}

		try (Cursor c = cr.query(uri, CONTENT_COLUMNS, dataQuery, dataArgs, null)) {
			if ((c != null) && c.moveToFirst()) {
				Uri img = ContentUris.withAppendedId(uri, c.getLong(0));
				if (!bitmapCache.isResourceImageAvailable(img) || !addFields(c, mb)) return false;
				mb.setImageUri(img.toString());
				return true;
			}
		}

		return false;
	}

	private Map<String, MetadataBuilder> queryMediaStore(BrowsableItem item) {
		if (!(item instanceof FolderItem)) return emptyMap();
		VirtualResource r = item.getResource();
		String path = null;

		if (r instanceof ContentFolder) {
			if (SDK_INT < VERSION_CODES.Q) return emptyMap();
			Uri uri = toMediaUri(r.getRid().toAndroidUri());
			if (uri == null) return emptyMap();

			try (Cursor c = App.get().getContentResolver()
					.query(uri, new String[]{"_data"}, null, null, null)) {
				if ((c != null) && c.moveToNext()) path = c.getString(0);
			}
		} else if ((r.getVirtualFileSystem() instanceof LocalFileSystem) &&
				(r instanceof VirtualFolder)) {
			path = item.getResource().getRid().getPath();
		}

		if (path == null) return emptyMap();
		String dataQuery;
		String[] dataArgs;
		String canonical = null;
		String id = item.getId();
		Map<String, MetadataBuilder> m = new HashMap<>();
		ContentResolver cr = App.get().getContentResolver();

		if (!(r instanceof ContentFolder)) {
			try {
				canonical = new File(path).getCanonicalPath();
			} catch (Throwable ignore) {
			}
		}

		if (canonical != null) {
			dataQuery = "(_data LIKE ? AND NOT _data LIKE ?) OR (_data LIKE ? AND NOT _data LIKE ?)";
			dataArgs = new String[]{canonical + "/%", canonical + "/%/%", path + "/%", path + "/%/%"};
		} else {
			dataQuery = "_data LIKE ? AND NOT _data LIKE ?";
			dataArgs = new String[]{path + "/%", path + "/%/%"};
		}

		id = FileItem.SCHEME + id.substring(FolderItem.SCHEME.length());
		queryMediaStore(cr, MediaStore.Audio.Media.getContentUri("external"), dataQuery, dataArgs, id,
				m);
		queryMediaStore(cr, MediaStore.Video.Media.getContentUri("external"), dataQuery, dataArgs, id,
				m);
		return m;
	}

	private void queryMediaStore(ContentResolver cr, Uri uri, String dataQuery, String[] dataArgs,
															 String idPref, Map<String, MetadataBuilder> m) {
		try (Cursor c = cr.query(uri, CONTENT_COLUMNS_DATA, dataQuery, dataArgs, null)) {
			if (c == null) return;
			while (c.moveToNext()) {
				String data = c.getString(CONTENT_COLUMNS_DATA.length - 1);
				if (data == null) continue;
				Uri img = ContentUris.withAppendedId(uri, c.getLong(0));
				if (!bitmapCache.isResourceImageAvailable(img)) continue;
				MetaBuilder mb = new MetaBuilder();
				if (!addFields(c, mb)) continue;
				int idx = data.lastIndexOf('/');
				if (idx < 0) continue;
				String id = idPref + '/' + data.substring(idx + 1);
				mb.setImageUri(img.toString());
				m.put(id, mb);
				insertMetadata(mb, id);
			}
		}
	}

	private static Uri toMediaUri(Uri uri) {
		if (SDK_INT >= VERSION_CODES.Q) {
			try {
				return MediaStore.getMediaUri(App.get(), uri);
			} catch (Exception ex) {
				Log.d(ex, "Failed to get media uri");
			}
		}
		return null;
	}

	private static boolean addFields(Cursor c, MetaBuilder mb) {
		try {
			long dur = c.getLong(2);
			if (dur <= 0) return false;
			mb.putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
		} catch (Exception ex) {
			Log.d(ex, "Invalid duration: ", c.getString(2));
			return false;
		}

		String s = c.getString(1);
		if (s != null) mb.putString(MediaMetadataCompat.METADATA_KEY_TITLE, s);
		s = c.getString(3);
		if (s != null) mb.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s);
		s = c.getString(4);
		if (s != null) mb.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, s);

		if (SDK_INT >= VERSION_CODES.R) {
			s = c.getString(5);
			if (s != null) mb.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, s);
			s = c.getString(6);
			if (s != null) mb.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, s);
			s = c.getString(7);
			if (s != null) mb.putString(MediaMetadataCompat.METADATA_KEY_GENRE, s);
		}

		return true;
	}

	public FutureSupplier<Map<String, MetadataBuilder>> queryMetadata(String idPattern,
																																		BrowsableItem br) {
		if (db == null) return queue.enqueue(() -> queryMediaStore(br));
		return queue.enqueue(() -> {
			Map<String, MetadataBuilder> m = query(idPattern);
			return m.isEmpty() ? queryMediaStore(br) : m;
		});
	}

	public FutureSupplier<String> queryId(String pattern) {
		return queryIds(pattern, 1).map(ids -> ids.isEmpty() ? null : ids.get(0));
	}

	public FutureSupplier<List<String>> queryIds(String pattern, int max) {
		return (db != null) ? queue.enqueue(() -> {
			List<String> ids = new ArrayList<>(max);
			try (Cursor c = db.query(TABLE, new String[]{COL_ID},
					COL_TITLE + " = ? OR " + COL_ARTIST + " = ? OR " + COL_ALBUM + " = ? LIMIT " + max,
					new String[]{pattern, pattern, pattern}, null, null, null)) {
				while (c.moveToNext()) ids.add(c.getString(0));
			}
			if (!ids.isEmpty()) return ids;

			String[] p = {'%' + pattern + '%'};
			try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_TITLE + " LIKE ?  LIMIT " + max, p,
					null, null, null)) {
				while (c.moveToNext()) ids.add(c.getString(0));
			}
			if (!ids.isEmpty()) return ids;
			try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_ARTIST + " LIKE ?  LIMIT + " + max,
					p, null, null, null)) {
				while (c.moveToNext()) ids.add(c.getString(0));
			}
			if (!ids.isEmpty()) return ids;
			try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_ALBUM + " LIKE ?  LIMIT " + max, p,
					null, null, null)) {
				while (c.moveToNext()) ids.add(c.getString(0));
			}
			return ids.isEmpty() ? emptyList() : ids;
		}) : completedEmptyList();
	}

	public FutureSupplier<Void> clearMetadata(String idPattern) {
		return (db != null) ? queue.enqueue(() -> clear(idPattern)) : completedVoid();
	}

	public void updateDuration(PlayableItem item, long duration) {
		if (db == null) return;

		queue.enqueue(() -> {
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
			if (count == 0) return emptyMap();

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
			return emptyMap();
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

		try (Cursor c = db.query(TABLE, QUERY_COLUMNS, COL_ID + " = ?", new String[]{item.getOrigId()},
				null, null, null); SharedTextBuilder tb = SharedTextBuilder.get()) {
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
		if (art != null) {
			String uri = null;
			if (art.length == SHA1_DIGEST_LEN) { // Old format in pure sha1
				uri = bitmapCache.getImageUri(art, tb);
			} else if ((art.length == SHA1_DIGEST_LEN + 1) && (art[art.length - 1] == ART_HASH)) {
				uri = bitmapCache.getImageUri(art, tb);
			} else if ((art.length > 1) && (art[art.length - 1] == ART_URI)) {
				uri = new String(art, 0, art.length - 1, UTF_8);
			}
			if (uri != null) meta.setImageUri(uri);
		}
	}

	private void insertMetadata(MetaBuilder meta, PlayableItem item) {
		insertMetadata(meta, item.getId());
	}

	private void insertMetadata(MetaBuilder meta, String id) {
		if ((db == null) || !meta.durationSet) return;
		Bitmap bm = meta.image;

		if (bm != null) {
			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				byte[] hash = bitmapCache.saveBitmap(bm, tb);

				if (hash != null) {
					meta.setImageUri(bitmapCache.getImageUri(hash, tb));
					byte[] art = Arrays.copyOf(hash, hash.length + 1);
					art[hash.length] = ART_HASH;
					meta.setArt(art);
				}
			}
		} else {
			String uri = meta.getImageUri();

			if (uri != null) {
				byte[] b = uri.getBytes(UTF_8);
				byte[] art = Arrays.copyOf(b, b.length + 1);
				art[b.length] = ART_URI;
				meta.setArt(art);
			}
		}

		meta.setId(id);
		meta.insert(db);
	}

	private void createTable() {
		if (db == null) return;

		PreferenceStore ps = FermataApplication.get().getPreferenceStore();
		Pref<IntSupplier> version = Pref.i("METADATA_VERSION", 0);

		if (ps.getIntPref(version) < 70) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE);
			ps.applyIntPref(version, BuildConfig.VERSION_CODE);
		}

		db.execSQL(
				"CREATE TABLE IF NOT EXISTS " + TABLE + "(" + COL_ID + " VARCHAR NOT NULL UNIQUE," + " " +
						COL_TITLE + " VARCHAR, " + COL_ALBUM + " VARCHAR, " + COL_ARTIST + " VARCHAR, " +
						COL_ALBUM_ARTIST + " VARCHAR, " + COL_COMPOSER + " VARCHAR, " + COL_WRITER +
						" VARCHAR, " + COL_GENRE + " VARCHAR, " + COL_DURATION + " INTEGER, " + COL_ART +
						" BLOB " + ");");
	}

	private static final class MetaBuilder extends MetadataBuilder {
		private final ContentValues values = new ContentValues(10);
		boolean durationSet;
		Bitmap image;

		@Override
		public void putString(String k, String v) {
			switch (k) {
				case MediaMetadataCompat.METADATA_KEY_TITLE -> values.put(COL_TITLE, v);
				case MediaMetadataCompat.METADATA_KEY_ALBUM -> values.put(COL_ALBUM, v);
				case MediaMetadataCompat.METADATA_KEY_ARTIST -> values.put(COL_ARTIST, v);
				case MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST -> values.put(COL_ALBUM_ARTIST, v);
				case MediaMetadataCompat.METADATA_KEY_COMPOSER -> values.put(COL_COMPOSER, v);
				case MediaMetadataCompat.METADATA_KEY_WRITER -> values.put(COL_WRITER, v);
				case MediaMetadataCompat.METADATA_KEY_GENRE -> values.put(COL_GENRE, v);
				case MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI -> {
					setImageUri(v);
					return;
				}
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

		void setId(String id) {
			values.put(COL_ID, id);
		}

		void setArt(byte[] art) {
			values.put(COL_ART, art);
		}

		void insert(SQLiteDatabase db) {
			db.insert(TABLE, null, values);
		}
	}
}
