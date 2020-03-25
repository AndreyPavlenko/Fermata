package me.aap.fermata.media.lib;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.function.Consumer;
import me.aap.utils.text.TextUtils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.BuildConfig.DEBUG;
import static me.aap.utils.collection.NaturalOrderComparator.compareNatural;
import static me.aap.utils.text.TextUtils.indexOfChar;

/**
 * @author Andrey Pavlenko
 */
class M3uItem extends BrowsableItemBase<Item> {
	public static final String SCHEME = "m3u";
	private final String name;
	private final String subtitle;
	private final List<Item> tracks;
	final String cover;

	private M3uItem(String id, BrowsableItem parent, MediaFile dir, MediaFile m3uFile) {
		super(id, parent, m3uFile);
		Context ctx = parent.getLib().getContext();
		Map<String, M3uGroupItem> groups = new LinkedHashMap<>();
		List<M3uTrackItem> tracks = new ArrayList<>();
		StringBuilder sb = TextUtils.getSharedStringBuilder();
		String idPath = id.substring(SCHEME.length());

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

		try (BufferedReader r = new BufferedReader(new InputStreamReader(requireNonNull(
				ctx.getContentResolver().openInputStream(m3uFile.getUri())), UTF_8))) {
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
						String value = l.substring(start, i).trim();

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
					group = l.substring(8);
					continue;
				} else if (l.startsWith("#PLAYLIST:")) {
					m3uName = l.substring(10).trim();
					continue;
				} else if (l.startsWith("#EXTALB:")) {
					if (first) m3uAlbum = l.substring(8).trim();
					else album = l.substring(8).trim();
					continue;
				} else if (l.startsWith("#EXTART:")) {
					if (first) m3uArtist = l.substring(8).trim();
					else artist = l.substring(8).trim();
					continue;
				} else if (l.startsWith("#EXTGENRE:")) {
					if (first) m3uGenre = l.substring(8).trim();
					else genre = l.substring(8).trim();
					continue;
				} else if (l.startsWith("#EXTIMG:")) {
					if (first) cover = l.substring(8).trim();
					else logo = l.substring(8).trim();
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

				MediaFile file = MediaFile.resolve(l, dir);

				if (file != null) {
					if (album == null) album = m3uAlbum;
					if (artist == null) artist = m3uArtist;
					if (genre == null) genre = m3uGenre;
					if (type == 0) type = m3uType;
					if (logo == null) logo = cover;

					if (group == null) {
						int tid = tracks.size();
						sb.setLength(0);
						sb.append(M3uTrackItem.SCHEME).append(':').append(-1).append(':')
								.append(tid).append(idPath);
						tracks.add(new M3uTrackItem(sb.toString(), this, tid, file, name, album, artist, genre,
								logo, duration, type));
					} else {
						M3uGroupItem g = groups.get(group);

						if (g == null) {
							int gid = groups.size();
							sb.setLength(0);
							sb.append(M3uGroupItem.SCHEME).append(':').append(gid).append(idPath);
							g = new M3uGroupItem(sb.toString(), this, group, gid);
							groups.put(group, g);
						}

						int tid = g.tracks.size();
						sb.setLength(0);
						sb.append(M3uTrackItem.SCHEME).append(':').append(g.getGroupId()).append(':')
								.append(tid).append(idPath);
						g.tracks.add(new M3uTrackItem(sb.toString(), g, tid, file, name, album, artist, genre,
								logo, duration, type));
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
			Log.e(getClass().getName(), "Failed to parse m3u file: " + m3uFile, ex);
		}

		int ngroups = groups.size();
		int ntracks = tracks.size();
		List<Item> children = new ArrayList<>(ngroups + ntracks);

		if (ngroups > 0) {
			for (M3uGroupItem g : groups.values()) {
				g.init();
				children.add(g);
			}

			Collections.sort(children, (c1, c2) -> compareNatural(c1.getName(), c2.getName()));
		}

		if (ntracks > 0) {
			children.addAll(tracks);
			Collections.sort(children.subList(ngroups, children.size()),
					(c1, c2) -> compareNatural(c1.getName(), c2.getName()));
		}

		this.name = (m3uName == null) ? m3uFile.getName() : m3uName;
		this.cover = cover;
		this.tracks = children;
		this.subtitle = getLib().getContext().getResources().getString(R.string.folder_subtitle,
				ngroups, ntracks);
	}

	static M3uItem create(String id, BrowsableItem parent, MediaFile dir, MediaFile m3uFile,
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

	static M3uItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		StringBuilder sb = TextUtils.getSharedStringBuilder();
		sb.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length());
		FileItem file = (FileItem) lib.getItem(sb);
		if (file == null) return null;

		FolderItem parent = (FolderItem) file.getParent();
		return create(id, parent, parent.getFile(), file.getFile(), lib);
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
	public String getSubtitle() {
		return subtitle;
	}

	@Override
	void buildMediaDescription(MediaDescriptionCompat.Builder b, Consumer<Consumer<MediaDescriptionCompat.Builder>> update) {
		super.buildMediaDescription(b, (cover != null) ? update : null);
	}

	@Override
	void loadMediaDescription(MediaDescriptionCompat.Builder b) {
		super.loadMediaDescription(b);
		if (cover == null) return;

		MediaFile file = MediaFile.resolve(cover, getFile().getParent());
		if (file != null) {
			Bitmap bm = getLib().getBitmap(file.getUri().toString());
			if (bm != null) b.setIconBitmap(bm);
		}
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
	public List<Item> listChildren() {
		return new ArrayList<>(tracks);
	}
}
