package me.aap.fermata.media.lib;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import me.aap.fermata.function.BooleanSupplier;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.Utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.BuildConfig.DEBUG;

/**
 * @author Andrey Pavlenko
 */
class CueItem extends BrowsableItemBase<CueTrackItem> {
	public static final String SCHEME = "cue";
	private static final Pref<BooleanSupplier> CUE_TITLE_NAME = TITLE_NAME.withDefaultValue(() -> true);
	private static final Pref<BooleanSupplier> CUE_TITLE_FILE_NAME = TITLE_FILE_NAME.withDefaultValue(() -> false);
	private static final Pref<BooleanSupplier> CUE_SUBTITLE_NAME = SUBTITLE_NAME.withDefaultValue(() -> false);
	private static final Pref<BooleanSupplier> CUE_SUBTITLE_FILE_NAME = SUBTITLE_FILE_NAME.withDefaultValue(() -> true);
	private final String name;
	private final String subtitle;
	private final List<CueTrackItem> tracks;

	private CueItem(String id, BrowsableItem parent, MediaFile dir, MediaFile cueFile) {
		super(id, parent, cueFile);

		FermataApplication app = FermataApplication.get();
		List<CueTrackItem> tracks = new ArrayList<>();
		MediaFile file = null;
		String fileName = null;
		String track = null;
		String title = null;
		String performer = null;
		String writer = null;
		String index = null;
		String albumTitle = null;
		String albumPerformer = null;
		String albumWriter = null;
		boolean isVideo = false;
		boolean wasTrack = false;
		MediaMetadataRetriever mmr = null;

		try (BufferedReader r = new BufferedReader(new InputStreamReader(requireNonNull(
				app.getContentResolver().openInputStream(cueFile.getUri())), UTF_8))) {
			for (String l = r.readLine(); l != null; l = r.readLine()) {
				l = l.trim();

				if (l.startsWith("REM ")) {
					//noinspection UnnecessaryContinue
					continue;
				} else if (l.startsWith("TRACK ")) {
					if (wasTrack) {
						addTrack(id, tracks, file, track, title, performer, writer, index, albumTitle,
								albumPerformer, albumWriter, isVideo);
						title = performer = writer = albumTitle = albumPerformer = albumWriter = null;
					} else {
						wasTrack = true;
					}

					track = l;
				} else if (l.startsWith("TITLE ")) {
					if (wasTrack) {
						title = rmQuotes(l.substring(6));
					} else {
						albumTitle = rmQuotes(l.substring(6));
					}
				} else if (l.startsWith("INDEX 01 ")) {
					index = rmQuotes(l.substring(9));
				} else if (l.startsWith("PERFORMER ")) {
					if (wasTrack) {
						performer = rmQuotes(l.substring(10));
					} else {
						albumPerformer = rmQuotes(l.substring(10));
					}
				} else if (l.startsWith("SONGWRITER ")) {
					if (wasTrack) {
						writer = rmQuotes(l.substring(11));
					} else {
						albumWriter = rmQuotes(l.substring(11));
					}
				} else if (l.startsWith("FILE ")) {
					if (wasTrack) {
						addTrack(id, tracks, file, track, title, performer, writer, index, albumTitle,
								albumPerformer, albumWriter, isVideo);
						track = title = performer = writer = index = albumTitle = albumPerformer = albumWriter = null;
					}

					String name = rmQuotes(l.substring(5));

					if (!name.equals(fileName)) {
						fileName = name;
						file = dir.getChild(name);
						isVideo = (file != null) && Utils.isVideoFile(file.getName());
					}
				}
			}

			addTrack(id, tracks, file, track, title, performer, writer, index, albumTitle,
					albumPerformer, albumWriter, isVideo);

			int size = tracks.size();

			if (size > 0) {
				CueTrackItem last = tracks.get(size - 1);
				mmr = new MediaMetadataRetriever();
				mmr.setDataSource(app, last.getFile().getUri());
				String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
				if (dur != null) {
					last.setDuration(Long.parseLong(dur) - last.getOffset());
				}
			}
		} catch (Exception ex) {
			Log.e("CueItem", "Failed to parse cue file: " + getFile(), ex);
		} finally {
			if (mmr != null) mmr.release();
		}

		this.tracks = tracks;
		this.name = (albumTitle != null) ? albumTitle : cueFile.getName();
		this.subtitle = getLib().getContext().getResources().getString(R.string.browsable_subtitle,
				tracks.size());
	}

	static CueItem create(String id, BrowsableItem parent, MediaFile dir, MediaFile cueFile,
												DefaultMediaLib lib) {
		MediaLib.Item i = lib.getFromCache(id);

		if (i != null) {
			CueItem c = (CueItem) i;
			if (DEBUG && !parent.equals(c.getParent())) throw new AssertionError();
			if (DEBUG && !cueFile.equals(c.getFile())) throw new AssertionError();
			return c;
		} else {
			return new CueItem(id, parent, dir, cueFile);
		}
	}

	static CueItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		StringBuilder sb = Utils.getSharedStringBuilder();
		sb.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length());
		FileItem file = (FileItem) lib.getItem(sb);
		if (file == null) return null;

		FolderItem parent = (FolderItem) file.getParent();
		return create(id, parent, parent.getFile(), file.getFile(), lib);
	}

	static boolean isCueFile(String name) {
		return name.endsWith(".cue");
	}

	private static String rmQuotes(String s) {
		int first = s.indexOf("\"");
		int last = s.lastIndexOf("\"");
		return (first < last) ? s.substring(first + 1, last) : s;
	}

	private void addTrack(String id, List<CueTrackItem> tracks, MediaFile file, String track, String title,
												String performer, String writer, String index, String albumTitle,
												String albumPerformer, String albumWriter, boolean isVideo) {
		if ((file == null) || (track == null) || (index == null)) return;

		String[] i = index.split(":");
		if (i.length != 3) return;

		long m = Long.parseLong(i[0]);
		int s = Integer.parseInt(i[1]);
		int f = Integer.parseInt(i[2]);
		long offset = m * 60000 + s * 1000 + (long) (((double) f / 74) * 1000);

		if (title == null) {
			title = (albumTitle != null) ? albumTitle : file.getName();
		}

		if ((performer == null) && (albumPerformer != null)) {
			performer = albumPerformer;
		}

		if ((writer == null) && (albumWriter != null)) {
			writer = albumWriter;
		}

		int trackNum = tracks.size() + 1;
		StringBuilder sb = Utils.getSharedStringBuilder();
		sb.append(CueTrackItem.SCHEME).append(':').append(trackNum)
				.append(id, SCHEME.length(), id.length());
		CueTrackItem t = new CueTrackItem(sb.toString(), this, trackNum, file, title,
				performer, writer, albumTitle, offset, isVideo);
		tracks.add(t);

		if (trackNum > 1) {
			CueTrackItem prev = tracks.get(trackNum - 2);
			prev.setDuration(offset - prev.getOffset());
		}
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

	public CueTrackItem getTrack(int id) {
		List<CueTrackItem> children = getChildren();
		if (id > children.size()) return null;

		CueTrackItem t = children.get(id - 1);
		if (t.getTrackNumber() == id) return t;

		for (CueTrackItem c : children) {
			if (c.getTrackNumber() == id) return c;
		}

		return null;
	}

	@Override
	public int getIcon() {
		return R.drawable.cue;
	}

	@Override
	public List<CueTrackItem> listChildren() {
		return new ArrayList<>(tracks);
	}

	@Override
	public Pref<BooleanSupplier> getTitleNamePrefKey() {
		return CUE_TITLE_NAME;
	}

	@Override
	public Pref<BooleanSupplier> getTitleFileNamePrefKey() {
		return CUE_TITLE_FILE_NAME;
	}

	@Override
	public Pref<BooleanSupplier> getSubtitleNamePrefKey() {
		return CUE_SUBTITLE_NAME;
	}

	@Override
	public Pref<BooleanSupplier> getSubtitleFileNamePrefKey() {
		return CUE_SUBTITLE_FILE_NAME;
	}
}
