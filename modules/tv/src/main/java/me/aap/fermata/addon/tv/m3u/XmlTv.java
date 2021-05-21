package me.aap.fermata.addon.tv.m3u;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader.Status;

import static me.aap.fermata.addon.tv.m3u.TvM3uFile.EPG_FILE_AGE;
import static me.aap.fermata.addon.tv.m3u.TvM3uTrackItem.EPG_ID_NOT_FOUND;
import static me.aap.fermata.addon.tv.m3u.TvM3uTrackItem.EPG_ID_UNKNOWN;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.collection.CollectionUtils.compute;
import static me.aap.utils.collection.CollectionUtils.computeIfAbsent;

/**
 * @author Andrey Pavlenko
 */
public class XmlTv implements Closeable {
	private static final String TABLE_CH = "Channels";
	private static final String TABLE_PROG = "Prog";
	private static final String TABLE_NAME_TO_ID = "NameToId";
	private static final String TABLE_NAME_TO_ICON = "NameToIcon";
	private static final String IDX_PROG_CH = "ProgChIdx";
	private static final String COL_ID = "Id";
	private static final String COL_EPG_ID = "EpgId";
	private static final String COL_NAME = "Name";
	private static final String COL_ICON = "Icon";
	private static final String COL_CH_ID = "ChId";
	private static final String COL_START = "Start";
	private static final String COL_STOP = "Stop";
	private static final String COL_TITLE = "Title";
	private static final String COL_DSC = "Dsc";
	private static final String[] Q_COL_ID_ICON = new String[]{COL_ID, COL_ICON};
	private static final String[] Q_COL_CH_ID = new String[]{COL_CH_ID};
	private static final String[] Q_COL_ICON = new String[]{COL_ICON};
	private static final String[] Q_COL_EPG = new String[]{COL_START, COL_STOP, COL_TITLE, COL_DSC, COL_ICON};
	private static final String Q_SEL_ID = COL_ID + " = ?";
	private static final String Q_SEL_EPG_ID = COL_EPG_ID + " = ?";
	private static final String Q_SEL_NAME = COL_NAME + " = ?";
	private static final String Q_SEL_EPG_ID_TIME = COL_CH_ID + " = ? AND " +
			COL_START + " <= ? AND " + COL_STOP + " > ?";
	private final SQLite sql;

	private XmlTv(SQLite sql) {
		this.sql = sql;
	}

	public boolean isClosed() {
		return sql.isClosed();
	}

	@Override
	public void close() {
		sql.close();
	}

	@Override
	protected void finalize() {
		close();
	}

	public static FutureSupplier<XmlTv> create(TvM3uItem item) {
		String url = item.getEpgUrl();
		if (url == null) return completedNull();

		try {
			XmlTv xml = new XmlTv(SQLite.get(item.getResource().getEpgDbFile()));
			FutureSupplier<FutureSupplier<XmlTv>> f = xml.sql.query(db -> {
				if (hasIndex(db)) {
					xml.load(item, true);
					return completed(xml);
				} else {
					return xml.load(item, false);
				}
			});

			if (f.isFailed()) {
				Log.e(f.getFailure(), "Failed to create XMLTV");
				return completedNull();
			} else {
				return f.getOrThrow();
			}
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	public FutureSupplier<Void> update(TvM3uTrackItem track) {
		if (track.getEpgId() == EPG_ID_NOT_FOUND) {
			Log.d("Channel not found - skipping update: ", track);
			return completedVoid();
		}

		if (sql.isClosed()) {
			Log.d("Database is closed: ", sql);
		}

		return sql.execute(db -> {
			try {
				updateTrack(db, track);
			} catch (Throwable ex) {
				Log.e(ex, "Failed to update channel: ", track.getName());
			}
		});
	}

	private static void updateTrack(SQLiteDatabase db, TvM3uTrackItem track) {
		int id = track.getEpgId();
		String icon = track.getEpgChIcon();

		if (id == EPG_ID_UNKNOWN) {
			String tvgId = track.getTvgId();
			String name = track.getName().toLowerCase();

			if (tvgId != null) {
				try (Cursor c = db.query(TABLE_CH, Q_COL_ID_ICON, Q_SEL_EPG_ID,
						new String[]{tvgId}, null, null, null)) {
					if (c.moveToFirst()) {
						id = c.getInt(0);
						icon = c.getString(1);
					}
				}

				if (id != EPG_ID_UNKNOWN) {
					try (Cursor c = db.query(TABLE_NAME_TO_ICON, Q_COL_ICON, Q_SEL_NAME,
							new String[]{name}, null, null, null)) {
						if (c.moveToFirst()) icon = c.getString(0);
					}
				}
			}

			if (id == EPG_ID_UNKNOWN) {
				try (Cursor c = db.query(TABLE_NAME_TO_ID, Q_COL_CH_ID, Q_SEL_NAME,
						new String[]{name}, null, null, null)) {
					if (c.moveToFirst()) id = c.getInt(0);
				}

				if (id != EPG_ID_UNKNOWN) {
					try (Cursor c = db.query(TABLE_NAME_TO_ICON, Q_COL_ICON, Q_SEL_NAME,
							new String[]{name}, null, null, null)) {
						if (c.moveToFirst()) icon = c.getString(0);
					}

					if (icon == null) {
						try (Cursor c = db.query(TABLE_CH, Q_COL_ICON, Q_SEL_ID,
								new String[]{String.valueOf(id)}, null, null, null)) {
							if (c.moveToFirst()) icon = c.getString(0);
						}
					}
				}
			}
		}

		if (id == EPG_ID_UNKNOWN) {
			Log.d("Channel not found: ", track.getName());
			track.update(EPG_ID_NOT_FOUND, null, 0, 0, null, null, null, false);
			return;
		}

		String time = String.valueOf(track.getM3uItem().getTime());

		try (Cursor c = db.query(TABLE_PROG, Q_COL_EPG, Q_SEL_EPG_ID_TIME,
				new String[]{String.valueOf(id), time, time}, null, null, null)) {
			if (c.moveToFirst()) {
				track.update(id, icon, c.getLong(0), c.getLong(1), c.getString(2),
						c.getString(3), c.getString(4), false);
			} else {
				track.update(id, icon, 0, 0, null, null, null, false);
			}
		}
	}

	private FutureSupplier<?> load(TvM3uItem item) {
		return sql.query(db -> load(item, hasIndex(db)));
	}

	private FutureSupplier<XmlTv> load(TvM3uItem item, boolean hasIndex) {
		return item.getResource().downloadEpg().then(status -> {
			if ((status.getDownloadedSize() == 0) && hasIndex) {
				Log.i("XMLTV is up to date: ", status.getUrl());
				return completed(this);
			}

			if (hasIndex) {
				Log.i("Scheduling XMLTV update in 30 seconds: ", status.getUrl());
				Async.schedule(() -> load(item, status), 30000);
				return completed(this);
			} else {
				return load(item, status);
			}
		}).onFailure(err -> {
			String url = item.getResource().getEpgUrl();
			if (hasIndex) {
				Log.e(err, "Failed to load XMLTV: ", url, ". Retrying in 5 minutes.");
				Async.schedule(() -> load(item), 5 * 60000);
			} else {
				Log.e(err, "Failed to load XMLTV: ", url);
				close();
			}
		}).onSuccess(v -> {
			TvM3uFile file = item.getResource();
			long s = file.getEpgTimeStamp();
			int a = file.getEpgMaxAge();
			long time = System.currentTimeMillis();
			if (s <= 0) s = time;
			if (a <= 0) a = EPG_FILE_AGE;
			long utime = s + a * 1000;
			if (utime <= time) utime = time + EPG_FILE_AGE * 1000;
			Log.i("Scheduling XMLTV update at ", new Date(utime));
			Async.scheduleAt(() -> !isClosed() ? load(item) : completedNull(), utime);
		});
	}

	private FutureSupplier<XmlTv> load(TvM3uItem item, Status status) {
		Map<String, List<TvM3uTrackItem>> idToTrack = new HashMap<>();
		Map<String, List<TvM3uTrackItem>> nameToTrack = new HashMap<>();
		return loadChannels(item, idToTrack, nameToTrack)
				.then(v -> sql.query(db -> loadXml(item, status, db, idToTrack, nameToTrack)));
	}

	private FutureSupplier<Void> loadChannels(BrowsableItem item,
																						Map<String, List<TvM3uTrackItem>> idToTrack,
																						Map<String, List<TvM3uTrackItem>> nameToTrack) {
		return item.getUnsortedChildren().then(children -> Async.forEach(c -> {
			if (c instanceof TvM3uTrackItem) {
				TvM3uTrackItem track = (TvM3uTrackItem) c;
				String id = track.getTvgId();
				if (id != null) computeIfAbsent(idToTrack, id, k -> new ArrayList<>(1)).add(track);
				computeIfAbsent(nameToTrack, track.getName().toLowerCase(), k -> new ArrayList<>(1)).add(track);
				return completedVoid();
			} else {
				return loadChannels((BrowsableItem) c, idToTrack, nameToTrack);
			}
		}, children));
	}

	private XmlTv loadXml(TvM3uItem item, Status status, SQLiteDatabase db,
												Map<String, List<TvM3uTrackItem>> idToTrack,
												Map<String, List<TvM3uTrackItem>> nameToTrack)
			throws ParserConfigurationException, SAXException, IOException {
		Log.i("Loading XMLTV: ", status.getUrl());
		long time = System.currentTimeMillis();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();

		try (InputStream fis = status.getFileStream(true)) {
			InputStream in = (status.getFile().getName().endsWith(".gz")) ? new GZIPInputStream(fis) : fis;
			createTables(db);
			db.beginTransaction();
			parser.parse(in, new XmlHandler(db, idToTrack, nameToTrack, item.getTime()));
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		Log.i("XMLTV has been successfully loaded in ", (System.currentTimeMillis() - time),
				" milliseconds: ", status.getUrl());
		return this;
	}

	private static void createTables(SQLiteDatabase db) {
		db.execSQL("DROP INDEX IF EXISTS " + IDX_PROG_CH);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_CH);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROG);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_TO_ID);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_TO_ICON);

		db.execSQL("CREATE TABLE " + TABLE_CH + '(' +
				COL_ID + " INTEGER PRIMARY KEY, " +
				COL_EPG_ID + " VARCHAR UNIQUE, " +
				COL_ICON + " VARCHAR" +
				");"
		);
		db.execSQL("CREATE TABLE " + TABLE_PROG + '(' +
				COL_CH_ID + " INTEGER, " +
				COL_START + " INTEGER, " +
				COL_STOP + " INTEGER, " +
				COL_TITLE + " VARCHAR, " +
				COL_DSC + " VARCHAR, " +
				COL_ICON + " VARCHAR" +
				");"
		);
		db.execSQL("CREATE TABLE " + TABLE_NAME_TO_ID + '(' +
				COL_NAME + " VARCHAR PRIMARY KEY, " +
				COL_CH_ID + " INTEGER" +
				");"
		);
		db.execSQL("CREATE TABLE " + TABLE_NAME_TO_ICON + '(' +
				COL_NAME + " VARCHAR PRIMARY KEY, " +
				COL_ICON + " VARCHAR NOT NULL" +
				");"
		);
	}

	private static boolean hasIndex(SQLiteDatabase db) {
		try (Cursor c = db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='index' AND name=?;",
				new String[]{IDX_PROG_CH})) {
			return c.moveToFirst() && (c.getInt(0) != 0);
		} catch (Throwable ex) {
			Log.d(ex, "Failed to get index");
			return false;
		}
	}

	private static final class XmlHandler extends DefaultHandler {
		private final SimpleDateFormat TIME = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault());
		private final SQLiteDatabase db;
		private final Map<String, List<TvM3uTrackItem>> idToTrack;
		private final Map<String, List<TvM3uTrackItem>> nameToTrack;
		private final long time;
		private final Map<String, ChannelInfo> channels;
		private final Map<String, InfoIcon> channelNames;
		private final String localLang = Locale.getDefault().getLanguage();
		private final SQLiteStatement chStmt;
		private final SQLiteStatement progStmt;
		private final SQLiteStatement nameToIdStmt;
		private final SQLiteStatement nameToIconStmt;
		private final Set<String> names = new HashSet<>();
		private final StringBuilder sb = new StringBuilder(1024);
		private String epgId;
		private String icon;
		private String start;
		private String stop;
		private String title;
		private String altTile;
		private String desc;
		private String altDesc;
		private Tag tag = Tag.IGNORE;
		private int counter;

		XmlHandler(SQLiteDatabase db, Map<String, List<TvM3uTrackItem>> idToTrack,
							 Map<String, List<TvM3uTrackItem>> nameToTrack, long time) {
			chStmt = db.compileStatement("INSERT INTO " + TABLE_CH + " VALUES(?, ?, ?)");
			progStmt = db.compileStatement("INSERT INTO " + TABLE_PROG + " VALUES(?, ?, ?, ?, ?, ?)");
			nameToIdStmt = db.compileStatement("INSERT INTO " + TABLE_NAME_TO_ID + " VALUES(?, ?)");
			nameToIconStmt = db.compileStatement("INSERT INTO " + TABLE_NAME_TO_ICON + " VALUES(?, ?)");
			this.db = db;
			this.idToTrack = idToTrack;
			this.nameToTrack = nameToTrack;
			this.time = time;
			int capacity = idToTrack.size() + nameToTrack.size();
			channels = new HashMap<>(capacity);
			channelNames = new HashMap<>(capacity);
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attrs) {
			switch (localName) {
				case "channel":
					tag = Tag.IGNORE;
					epgId = attrs.getValue("id");
					break;
				case "display-name":
					tag = Tag.DISPLAY_NAME;
					break;
				case "icon":
					tag = Tag.IGNORE;
					icon = attrs.getValue("src");
					break;
				case "programme":
					tag = Tag.IGNORE;
					epgId = attrs.getValue("channel");
					start = attrs.getValue("start");
					stop = attrs.getValue("stop");
					break;
				case "title":
					tag = localLang.equals(attrs.getValue("lang")) ? Tag.TITLE : Tag.TITLE_ALT;
					break;
				case "desc":
					tag = localLang.equals(attrs.getValue("lang")) ? Tag.DESC : Tag.DESC_ALT;
					break;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			switch (tag) {
				case DISPLAY_NAME:
				case TITLE:
				case TITLE_ALT:
				case DESC:
				case DESC_ALT:
					sb.append(ch, start, length);
					break;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			switch (localName) {
				case "channel":
					addChannel();
					return;
				case "programme":
					addProg();
					return;
			}

			switch (tag) {
				case DISPLAY_NAME:
					names.add(sb.toString().trim().toLowerCase());
					break;
				case TITLE:
					title = sb.toString().trim();
					break;
				case TITLE_ALT:
					altTile = sb.toString().trim();
					break;
				case DESC:
					desc = sb.toString().trim();
					break;
				case DESC_ALT:
					altDesc = sb.toString().trim();
					break;
			}

			tag = Tag.IGNORE;
			sb.setLength(0);
		}

		@Override
		public void endDocument() {
			Log.d("Inserting ", channels.size(), " channels");
			for (Map.Entry<String, ChannelInfo> e : channels.entrySet()) {
				try {
					ChannelInfo i = e.getValue();
					chStmt.clearBindings();
					chStmt.bindLong(1, i.id);
					bindString(chStmt, 2, e.getKey());
					bindString(chStmt, 3, i.icon);
					chStmt.execute();
				} catch (Exception ex) {
					Log.e(ex, "Failed to insert channel: ", e.getKey());
				}
			}

			Log.d("Inserting ", channelNames.size(), " channel names");
			for (Map.Entry<String, InfoIcon> e : channelNames.entrySet()) {
				try {
					InfoIcon i = e.getValue();
					nameToIdStmt.clearBindings();
					nameToIdStmt.bindString(1, e.getKey());
					nameToIdStmt.bindLong(2, i.info.id);
					nameToIdStmt.execute();

					if ((i.icon != null) && !i.icon.equals(i.info.icon)) {
						nameToIconStmt.clearBindings();
						nameToIconStmt.bindString(1, e.getKey());
						nameToIconStmt.bindString(2, i.icon);
						nameToIconStmt.execute();
					}
				} catch (Exception ex) {
					Log.e(ex, "Failed to insert channel name: ", e.getKey());
				}
			}

			Log.i("Creating XMLTV index");
			db.execSQL("CREATE INDEX " + IDX_PROG_CH + " ON " + TABLE_PROG + '(' + COL_CH_ID + ");");
		}

		private void addChannel() {
			if (!isEmpty(epgId)) {
				List<TvM3uTrackItem> tracks = idToTrack.get(epgId);
				ChannelInfo info = (tracks != null) ? createChannel(tracks) : null;

				for (String name : names) {
					tracks = nameToTrack.get(name);
					if (tracks == null) continue;

					if (info == null) info = createChannel(tracks);
					else info.addTracks(tracks);
					ChannelInfo i = info;
					compute(channelNames, name, (k, v) -> {
						if (v == null) return new InfoIcon(i, icon);
						else if (v.icon == null) v.icon = icon;
						return v;
					});

					for (TvM3uTrackItem t : tracks) {
						if (!Objects.equals(icon, t.getEpgChIcon())) {
							t.update(t.getEpgId(), icon, t.getEpgStart(), t.getEpgStop(), t.getEpgTitle(),
									t.getEpgDesc(), t.getEpgProgIcon(), true);
						}
					}
				}
			}

			epgId = icon = null;
			names.clear();
		}

		private ChannelInfo createChannel(List<TvM3uTrackItem> tracks) {
			ChannelInfo info = compute(channels, epgId, (k, v) -> {
				if (v == null) return new ChannelInfo(counter++, icon);
				else if (v.icon == null) v.icon = icon;
				return v;
			});
			assert info != null;
			info.addTracks(tracks);
			return info;
		}

		private void addProg() {
			ChannelInfo info;

			if (!isEmpty(epgId) && ((info = channels.get(epgId)) != null)) {
				long start = toTime(this.start);
				long stop = toTime(this.stop);
				String t = (title != null) ? title : altTile;
				String d = (desc != null) ? desc : altDesc;

				try {
					progStmt.clearBindings();
					progStmt.bindLong(1, info.id);
					progStmt.bindLong(2, start);
					progStmt.bindLong(3, stop);
					bindString(progStmt, 4, t);
					bindString(progStmt, 5, d);
					bindString(progStmt, 6, icon);
					progStmt.execute();
				} catch (Exception ex) {
					Log.e(ex, "Failed to insert programme: ", epgId);
				}

				if ((start <= time) && (stop > time)) {
					for (TvM3uTrackItem tr : info.tracks) {
						tr.update(info.id, tr.getEpgChIcon(), start, stop, t, d, icon, true);
					}
				}
			}

			epgId = start = stop = icon = title = altTile = desc = altDesc = null;
		}

		private void bindString(SQLiteStatement stmt, int index, String value) {
			if (value == null) stmt.bindNull(index);
			else stmt.bindString(index, value);
		}

		private long toTime(String time) {
			if (time == null) return 0;

			try {
				Date d = TIME.parse(time);
				return (d != null) ? d.getTime() : 0;
			} catch (ParseException ex) {
				Log.e(ex, "Failed to parse time: ", time);
				return 0;
			}
		}

		private boolean isEmpty(String s) {
			return (s == null) || s.isEmpty();
		}

		private enum Tag {
			IGNORE, DISPLAY_NAME, TITLE, TITLE_ALT, DESC, DESC_ALT
		}

		private static final class ChannelInfo {
			final Set<TvM3uTrackItem> tracks = new HashSet<>();
			final int id;
			String icon;

			ChannelInfo(int id, String icon) {
				this.id = id;
				this.icon = icon;
			}

			void addTracks(List<TvM3uTrackItem> tracks) {
				this.tracks.addAll(tracks);

				for (TvM3uTrackItem t : tracks) {
					if (t.getEpgId() < 0) {
						t.update(id, icon, t.getEpgStart(), t.getEpgStop(), t.getEpgTitle(),
								t.getEpgDesc(), t.getEpgProgIcon(), true);
					}
				}
			}
		}

		private static final class InfoIcon {
			final ChannelInfo info;
			String icon;

			InfoIcon(ChannelInfo info, String icon) {
				this.info = info;
				this.icon = icon;
			}
		}
	}
}
