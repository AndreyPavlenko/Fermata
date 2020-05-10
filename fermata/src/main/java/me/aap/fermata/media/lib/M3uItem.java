package me.aap.fermata.media.lib;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualResource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.aap.fermata.BuildConfig.DEBUG;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.text.TextUtils.indexOfChar;

/**
 * @author Andrey Pavlenko
 */
class M3uItem extends BrowsableItemBase {
	public static final String SCHEME = "m3u";
	private final String name;
	private final String subtitle;
	private final List<Item> tracks;
	private final String cover;
	private FutureSupplier<Uri> iconUri;

	private M3uItem(String id, BrowsableItem parent, VirtualResource dir, VirtualFile m3uFile) {
		super(id, parent, m3uFile);
		Map<String, M3uGroupItem> groups = new LinkedHashMap<>();
		List<M3uTrackItem> tracks = new ArrayList<>();
		String idPath = id.substring(SCHEME.length());
		VfsManager vfs = getLib().getVfsManager();

		String m3uName = null;
		String m3uAlbum = null;
		String m3uArtist = null;
		String m3uGenre = null;
		String cover = null;
		byte m3uType = 0; // 0 - unknown, 1 - audio, 2 - video

		String name = null;
		String group = null;
		String album = null;
		String artist = null;
		String genre = null;
		String logo = null;
		long duration = 0;
		byte type = 0;
		boolean first = true;

		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				m3uFile.getInputStream().asInputStream(), UTF_8))) {
			read:
			for (String l = r.readLine(); l != null; l = r.readLine()) {
				l = l.trim();

				if (l.isEmpty()) {
					continue;
				} else if (l.startsWith("#EXTINF:")) {
					first = false;
					int len = l.length();
					int i = indexOfChar(l, 8, len, " ,");
					if (i == -1) continue;

					duration = TextUtils.toLong(l, 8, i, 0);

					for (; ; ) {
						if (l.charAt(i) == ',') {
							name = l.substring(i + 1);
							continue read;
						}

						int start = i + 1;
						i = indexOfChar(l, start, len, "=,");
						if (i == -1) continue read;
						if (l.charAt(i) != '=') continue;

						String key = l.substring(start, i).trim();
						i = indexOfChar(l, i + 1, len, "\",");
						if (i == -1) continue read;
						if (l.charAt(i) != '\"') continue;

						start = i + 1;
						i = indexOfChar(l, start, len, "\",");
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

		this.name = (m3uName == null) ? m3uFile.getName() : m3uName;
		this.cover = cover;
		this.tracks = children;
		this.subtitle = getLib().getContext().getResources().getString(R.string.folder_subtitle,
				ntracks, ngroups);
	}

	@NonNull
	static M3uItem create(String id, BrowsableItem parent, VirtualResource dir, VirtualFile m3uFile,
												DefaultMediaLib lib) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				M3uItem c = (M3uItem) i;
				if (DEBUG && !parent.equals(c.getParent())) throw new AssertionError();
				if (DEBUG && !m3uFile.equals(c.getFile())) throw new AssertionError();
				return c;
			} else {
				return new M3uItem(id, parent, dir, m3uFile);
			}
		}
	}

	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length());

		return lib.getItem(tb.releaseString()).map(i -> {
			FileItem file = (FileItem) i;
			if (file == null) return null;

			FolderItem parent = (FolderItem) file.getParent();
			return create(id, parent, parent.getFile(), (VirtualFile) file.getFile(), lib);
		});
	}

	static boolean isM3uFile(String name) {
		return name.endsWith(".m3u");
	}

	@Override
	public String getName() {
		return name;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		if (iconUri == null) {
			if (cover == null) {
				iconUri = completedNull();
			} else {
				return getFile().getParent().then(folder -> {
					if (folder == null) return iconUri = completedNull();
					return getLib().getVfsManager().resolve(cover, folder).then(file ->
							iconUri = (file != null) ? completed(file.getRid().toAndroidUri()) : completedNull());
				});
			}
		}

		return iconUri;
	}

	public M3uTrackItem getTrack(int id) {
		for (Item c : tracks) {
			if (c instanceof M3uTrackItem) {
				M3uTrackItem t = (M3uTrackItem) c;
				if (t.getTrackNumber() == id) return t;
			}
		}
		return null;
	}

	public M3uTrackItem getTrack(int gid, int tid) {
		if (gid == -1) return getTrack(tid);
		M3uGroupItem g = getGroup(gid);
		return (g != null) ? g.getTrack(tid) : null;
	}

	public M3uGroupItem getGroup(int id) {
		for (Item c : tracks) {
			if (c instanceof M3uGroupItem) {
				M3uGroupItem g = (M3uGroupItem) c;
				if (g.getGroupId() == id) return g;
			}
		}
		return null;
	}

	@Override
	public int getIcon() {
		return R.drawable.m3u;
	}

	@Override
	public FutureSupplier<List<Item>> listChildren() {
		return completed(tracks);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed(subtitle);
	}

	private static String trim(String s) {
		return (s = s.trim()).isEmpty() ? null : s;
	}
}
