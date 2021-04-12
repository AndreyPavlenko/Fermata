package me.aap.fermata.media.lib;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
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

import static me.aap.fermata.BuildConfig.DEBUG;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.text.TextUtils.indexOfChar;

/**
 * @author Andrey Pavlenko
 */
class M3uItem extends BrowsableItemBase {
	public static final String SCHEME = "m3u";
	private FutureSupplier<Uri> iconUri;
	final FutureRef<Data> data = new FutureRef<Data>() {
		@Override
		protected FutureSupplier<Data> create() {
			return App.get().execute(M3uItem.this::parse);
		}
	};

	private M3uItem(String id, BrowsableItem parent, VirtualFile m3uFile) {
		super(id, parent, m3uFile);
	}

	private Data parse() {
		String id = getId();
		VirtualFile m3uFile = (VirtualFile) getResource();
		VirtualFolder dir = m3uFile.getParent().getOrThrow();
		Map<String, M3uGroupItem> groups = new LinkedHashMap<>();
		List<M3uTrackItem> tracks = new ArrayList<>();
		String idPath = id.substring(SCHEME.length());
		VfsManager vfs = getLib().getVfsManager();

		String m3uName = null;
		String m3uAlbum = null;
		String m3uArtist = null;
		String m3uGenre = null;
		String cover = null;
		byte m3uType = isVideo(m3uFile) ? (byte) 2 : 1; // 0 - unknown, 1 - audio, 2 - video

		String name = null;
		String group = null;
		String album = null;
		String artist = null;
		String genre = null;
		String logo = null;
		long duration = 0;
		byte type = 0;
		boolean first = true;

		try (BufferedReader r = new BufferedReader(createReader(m3uFile))) {
			read:
			for (String l = r.readLine(); l != null; l = r.readLine()) {
				l = l.trim();

				if (l.isEmpty()) {
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
						String value = trim(l.substring(start, i));

						switch (key) {
							case "logo":
							case "tvg-logo":
								logo = value;
								break;
							case "group-title":
								group = value;
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
						int tid = tracks.size();
						SharedTextBuilder tb = SharedTextBuilder.get();
						tb.append(M3uTrackItem.SCHEME).append(':').append(-1).append(':')
								.append(tid).append(idPath);
						tracks.add(new M3uTrackItem(tb.releaseString(), this, tid, file, name, album, artist, genre,
								logo, duration, type));
					} else {
						M3uGroupItem g = groups.get(group);

						if (g == null) {
							int gid = groups.size();
							SharedTextBuilder tb = SharedTextBuilder.get();
							tb.append(M3uGroupItem.SCHEME).append(':').append(gid).append(idPath);
							g = new M3uGroupItem(tb.releaseString(), this, group, gid);
							groups.put(group, g);
						}

						int tid = g.tracks.size();
						SharedTextBuilder tb = SharedTextBuilder.get();
						tb.append(M3uTrackItem.SCHEME).append(':').append(g.getGroupId()).append(':')
								.append(tid).append(idPath);
						g.tracks.add(new M3uTrackItem(tb.releaseString(), g, tid, file, name, album, artist,
								genre, logo, duration, type));
					}
				}

				name = null;
				group = null;
				album = null;
				artist = null;
				genre = null;
				logo = null;
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
				if (DEBUG && !parent.equals(c.getParent())) throw new AssertionError();
				if (DEBUG && !m3uFile.equals(c.getResource())) throw new AssertionError();
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
		return fs.getResource(fs.toRid(id.substring(SCHEME.length() + 1))).main().then(r -> {
			if (r != null) return completed(create(id, lib.getFolders(), (VirtualFile) r, lib));

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

	@Override
	public String getName() {
		Data d = data.get().peek();
		return (d == null) ? super.getName() : d.name;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		if (iconUri == null) {
			return data.get().then(d -> {
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

	public FutureSupplier<Item> getTrack(int id) {
		return data.get().map(d -> {
			for (Item c : d.tracks) {
				if (c instanceof M3uTrackItem) {
					M3uTrackItem t = (M3uTrackItem) c;
					if (t.getTrackNumber() == id) return t;
				}
			}
			return null;
		});
	}

	public FutureSupplier<Item> getTrack(int gid, int tid) {
		if (gid == -1) return getTrack(tid);
		return getGroup(gid).map(g -> (g != null) ? ((M3uGroupItem) g).getTrack(tid) : null);
	}

	public FutureSupplier<Item> getGroup(int id) {
		return data.get().map(d -> {
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
		return data.get().map(d -> d.tracks);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return data.get().map(d -> d.subtitle);
	}

	@NonNull
	@Override
	public FutureSupplier<Void> refresh() {
		Data d = data.peek();
		VirtualResource r = getResource();

		if (d != null) {
			data.clear();
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
		}

		if (r.getVirtualFileSystem() instanceof M3uFileSystem) {
			M3uFileSystem.getInstance().reload(r.getRid());
		}

		return super.refresh();
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

	private boolean isVideo(VirtualFile m3uFile) {
		if (m3uFile.getVirtualFileSystem() instanceof M3uFileSystem) {
			return M3uFileSystem.getInstance().isVideo(m3uFile.getRid());
		} else {
			return false;
		}
	}

	private static String trim(String s) {
		return (s = s.trim()).isEmpty() ? null : s;
	}

	private static final class Data {
		final String name;
		final String subtitle;
		final List<Item> tracks;
		final String cover;

		Data(String name, String subtitle, List<Item> tracks, String cover) {
			this.name = name;
			this.subtitle = subtitle;
			this.tracks = tracks;
			this.cover = cover;
		}
	}
}
