package me.aap.fermata.media.lib;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.fermata.vfs.m3u.M3uFileSystem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.text.TextUtils.indexOf;
import static me.aap.utils.text.TextUtils.indexOfChar;

/**
 * @author Andrey Pavlenko
 */
public class M3uItem extends BrowsableItemBase {
	public static final String SCHEME = "m3u";
	private FutureSupplier<Uri> iconUri;
	private final FutureRef<Data> data = new FutureRef<Data>() {
		@Override
		protected FutureSupplier<Data> create() {
			return App.get().execute(M3uItem.this::parse)
					.ifFail(err -> {
						Log.e(err, "Failed to load M3U: ", getResource().getName());
						return new Data(getResource().getName(), "", Collections.emptyList(), null);
					});
		}
	};

	protected M3uItem(String id, BrowsableItem parent, VirtualFile m3uFile) {
		super(id, parent, m3uFile);
	}

	private Data parse() {
		String id = getId();
		VirtualFile m3uFile = (VirtualFile) getResource();
		VirtualFolder dir = m3uFile.getParent().peek();
		Map<String, M3uGroupItem> groups = new LinkedHashMap<>();
		List<M3uTrackItem> tracks = new ArrayList<>();
		String idPath = id.substring(getScheme().length());
		VfsManager vfs = getLib().getVfsManager();

		String m3uName = null;
		String m3uAlbum = null;
		String m3uArtist = null;
		String m3uGenre = null;
		String cover = null;
		byte m3uType = isVideo(m3uFile) ? (byte) 2 : 0; // 0 - unknown, 1 - audio, 2 - video

		String name = null;
		String group = null;
		String album = null;
		String artist = null;
		String genre = null;
		String logo = null;
		String tvgId = null;
		String tvgName = null;
		long duration = 0;
		byte type = 0;
		boolean first = true;

		try (BufferedReader r = new BufferedReader(createReader(m3uFile))) {
			read:
			for (String l = r.readLine(); l != null; l = r.readLine()) {
				l = l.trim();

				if (l.isEmpty()) {
					continue;
				} else if (l.startsWith("#EXTM3U")) {
					for (int off = 7, len = l.length(); off < len; ) {
						int i = indexOf(l, '=', off, len);
						if (i == -1) continue read;
						String key = l.substring(off, i).trim();

						if ((++i != len) && (l.charAt(i) == '"')) {
							off = i + 1;
							i = indexOf(l, '\"', off, len);
							if (i == -1) i = len;
						} else {
							off = i;
							i = indexOf(l, ' ', off, len);
							if (i == -1) i = len;
						}

						switch (key) {
							case "tvg-url":
							case "url-tvg":
								setTvgUrl(trim(l.substring(off, i)));
								break;
							case "catchup":
								setCatchup(trim(l.substring(off, i)));
								break;
							case "catchup-source":
								setCatchupSource(trim(l.substring(off, i)));
								break;
							case "catchup-days":
								setCatchupDays(trim(l.substring(off, i)));
								break;
						}

						off = i + 1;
					}

					continue;
				} else if (l.startsWith("#EXTINF:")) {
					first = false;
					int len = l.length();
					int i = indexOfChar(l, " ,", 8, len);
					if (i == -1) continue;

					duration = TextUtils.toLong(l, 8, i, 0);

					for (; ; ) {
						if (l.charAt(i) == ',') {
							name = l.substring(i + 1);
							continue read;
						}

						int start = i + 1;
						i = indexOfChar(l, "=,", start, len);
						if (i == -1) continue read;
						if (l.charAt(i) != '=') continue;

						String key = l.substring(start, i).trim();
						i = indexOfChar(l, "\",", i + 1, len);
						if (i == -1) continue read;
						if (l.charAt(i) != '\"') continue;

						start = i + 1;
						i = indexOfChar(l, "\",", start, len);
						if (i == -1) continue read;
						if (l.charAt(i) != '\"') continue;

						switch (key) {
							case "logo":
							case "tvg-logo":
								logo = trim(l.substring(start, i));
								break;
							case "tvg-id":
								tvgId = trim(l.substring(start, i));
								break;
							case "tvg-name":
								tvgName = trim(l.substring(start, i));
								break;
							case "group-title":
								group = trim(l.substring(start, i));
								break;
						}
					}
				} else if (l.startsWith("#EXTGRP:")) {
					group = trim(l.substring(8));
					continue;
				} else if (l.startsWith("#PLAYLIST:")) {
					m3uName = trim(l.substring(10));
					continue;
				} else if (l.startsWith("#EXTALB:")) {
					if (first) m3uAlbum = trim(l.substring(8));
					else album = trim(l.substring(8));
					continue;
				} else if (l.startsWith("#EXTART:")) {
					if (first) m3uArtist = trim(l.substring(8));
					else artist = trim(l.substring(8));
					continue;
				} else if (l.startsWith("#EXTGENRE:")) {
					if (first) m3uGenre = trim(l.substring(8));
					else genre = trim(l.substring(8));
					continue;
				} else if (l.startsWith("#EXTIMG:")) {
					if (first) cover = trim(l.substring(8));
					else logo = trim(l.substring(8));
					continue;
				} else if (l.startsWith("#EXT-X-MEDIA:")) {
					byte t = 0;
					if (l.contains("TYPE=AUDIO")) t = 1;
					else if (l.contains("TYPE=VIDEO")) t = 2;

					if (t != 0) {
						if (first) m3uType = t;
						else type = t;
					}

					continue;
				} else if (l.startsWith("#")) {
					continue;
				}

				VirtualResource file = vfs.resolve(l, dir).get(null);

				if (file != null) {
					if (album == null) album = m3uAlbum;
					if (artist == null) artist = m3uArtist;
					if (genre == null) genre = m3uGenre;
					if (type == 0) type = m3uType;
					if (logo == null) logo = cover;

					if (group == null) {
						tracks.add(createTrack(this, -1, tracks.size(), idPath, file, name, album, artist, genre,
								logo, tvgId, tvgName, duration, type));
					} else {
						M3uGroupItem g = groups.get(group);

						if (g == null) {
							g = createGroup(idPath, group, groups.size());
							groups.put(group, g);
						}

						g.tracks.add(createTrack(g, g.getGroupId(), g.tracks.size(), idPath, file, name, album,
								artist, genre, logo, tvgId, tvgName, duration, type));
					}
				}

				name = group = album = artist = genre = logo = tvgId = tvgName = null;
				duration = 0;
				type = 0;
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to parse m3u file: ", m3uFile);
		}

		int ngroups = groups.size();
		int ntracks = tracks.size();
		List<Item> children = new ArrayList<>(ngroups + ntracks);

		if (ngroups > 0) {
			for (M3uGroupItem g : groups.values()) {
				g.init();
				children.add(g);
			}
		}

		if (ntracks > 0) {
			children.addAll(tracks);
		}

		String title = (m3uName == null) ? m3uFile.getName() : m3uName;
		String subtitle = getLib().getContext().getResources().getString(R.string.folder_subtitle,
				ntracks, ngroups);
		return new Data(title, subtitle, children, cover);
	}

	@NonNull
	static M3uItem create(String id, BrowsableItem parent, VirtualFile m3uFile,
												DefaultMediaLib lib) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				M3uItem c = (M3uItem) i;
				if (BuildConfig.D && !parent.equals(c.getParent())) throw new AssertionError();
				if (BuildConfig.D && !m3uFile.equals(c.getResource())) throw new AssertionError();
				return c;
			} else {
				return new M3uItem(id, parent, m3uFile);
			}
		}
	}

	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);
			if (i != null) return completed(i);
		}

		M3uFileSystem fs = M3uFileSystem.getInstance();
		return fs.getResource(fs.toRid(id.substring(SCHEME.length() + 1)))
				.ifFail(err -> {
					Log.e(err, "Failed to load resource");
					return null;
				}).main().then(r -> {
					if (r != null) return completed(create(id, lib.getFolders(), r, lib));

					SharedTextBuilder tb = SharedTextBuilder.get();
					tb.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length());

					return lib.getItem(tb.releaseString()).map(i -> {
						if (!(i instanceof FileItem)) {
							Log.w("Resource not found: ", id);
							return null;
						}

						FileItem file = (FileItem) i;
						BrowsableItem parent = file.getParent();
						return create(id, parent, (VirtualFile) file.getResource(), lib);
					});
				});
	}

	static boolean isM3uFile(String name) {
		return name.endsWith(".m3u");
	}

	protected FutureRef<Data> getData() {
		return data;
	}

	protected String getScheme() {
		return SCHEME;
	}

	protected M3uGroupItem createGroup(String idPath, String name, int groupId) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uGroupItem.SCHEME).append(':').append(groupId).append(idPath);
		return new M3uGroupItem(tb.releaseString(), this, name, groupId);
	}

	protected M3uTrackItem createTrack(BrowsableItem parent, int groupNumber, int trackNumber,
																		 String idPath, VirtualResource file, String name, String album,
																		 String artist, String genre, String logo, String tvgId,
																		 String tvgName, long duration, byte type) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uTrackItem.SCHEME).append(':').append(groupNumber).append(':')
				.append(trackNumber).append(idPath);
		return new M3uTrackItem(tb.releaseString(), parent, trackNumber, file, name, album, artist,
				genre, logo, tvgId, tvgName, duration, type);
	}

	@NonNull
	@Override
	public String getName() {
		Data d = getData().get().peek();
		return (d == null) || (d.name == null) ? super.getName() : d.name;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		if (iconUri == null) {
			return getData().get().then(d -> {
				if (d.cover == null) {
					return iconUri = completedNull();
				} else {
					return getResource().getParent().then(folder -> {
						if (folder == null) return iconUri = completedNull();
						return getLib().getVfsManager().resolve(d.cover, folder).then(file ->
								iconUri = (file != null) ? completed(file.getRid().toAndroidUri()) : completedNull());
					});
				}
			});
		}

		return iconUri;
	}

	public FutureSupplier<? extends M3uTrackItem> getTrack(int id) {
		return getData().get().map(d -> {
			for (Item c : d.tracks) {
				if (c instanceof M3uTrackItem) {
					M3uTrackItem t = (M3uTrackItem) c;
					if (t.getTrackNumber() == id) return t;
				}
			}
			return null;
		});
	}

	public FutureSupplier<? extends M3uTrackItem> getTrack(int gid, int tid) {
		if (gid == -1) return getTrack(tid);
		return getGroup(gid).map(g -> (g != null) ? g.getTrack(tid) : null);
	}

	public FutureSupplier<? extends M3uGroupItem> getGroup(int id) {
		return getData().get().map(d -> {
			for (Item c : d.tracks) {
				if (c instanceof M3uGroupItem) {
					M3uGroupItem g = (M3uGroupItem) c;
					if (g.getGroupId() == id) return g;
				}
			}
			return null;
		});
	}

	@Override
	public int getIcon() {
		return R.drawable.m3u;
	}

	@Override
	public FutureSupplier<List<Item>> listChildren() {
		return getData().get().map(d -> d.tracks);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return getData().get().map(d -> d.subtitle);
	}

	@NonNull
	@Override
	public FutureSupplier<Void> refresh() {
		FutureRef<Data> ref = getData();
		if (ref.peek() == null) return super.refresh();

		return ref.get().main().then(d -> {
			ref.clear();
			DefaultMediaLib lib = (DefaultMediaLib) getLib();

			synchronized (lib.cacheLock()) {
				lib.removeFromCache(this);

				for (Item i : d.tracks) {
					if (i instanceof M3uGroupItem) {
						for (M3uTrackItem t : ((M3uGroupItem) i).tracks) {
							lib.removeFromCache(t);
						}
					}

					lib.removeFromCache(i);
				}
			}

			return super.refresh();
		});
	}

	protected Reader createReader(VirtualFile m3uFile) throws IOException {
		InputStream in = m3uFile.getInputStream().asInputStream();
		String enc = m3uFile.getContentEncoding();
		String cs = m3uFile.getCharacterEncoding();

		if (enc != null) {
			if ("gzip".equals(enc)) {
				in = new GZIPInputStream(in);
			} else if ("deflate".equals(enc)) {
				in = new InflaterInputStream(in);
			} else {
				throw new IOException("Unsupported content encoding: " + enc);
			}
		}

		return new InputStreamReader(in, (cs == null) ? "UTF-8" : cs);
	}

	protected void setTvgUrl(String url) {
	}

	protected void setCatchup(String catchup) {
	}

	protected void setCatchupSource(String src) {
	}

	protected void setCatchupDays(String days) {
	}

	private boolean isVideo(VirtualFile f) {
		return (f instanceof M3uFile) && ((M3uFile) f).isVideo();
	}

	private static String trim(String s) {
		return (s = s.trim()).isEmpty() ? null : s;
	}

	protected static final class Data {
		protected final String name;
		protected final String subtitle;
		protected final List<Item> tracks;
		protected final String cover;

		Data(String name, String subtitle, List<Item> tracks, String cover) {
			this.name = name;
			this.subtitle = subtitle;
			this.tracks = tracks;
			this.cover = cover;
		}
	}
}
