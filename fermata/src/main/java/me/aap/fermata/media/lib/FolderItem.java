package me.aap.fermata.media.lib;

import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FolderItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.Utils;
import me.aap.utils.io.FileUtils;
import me.aap.utils.text.TextUtils;

import static me.aap.fermata.BuildConfig.DEBUG;

/**
 * @author Andrey Pavlenko
 */
class FolderItem extends BrowsableItemBase<Item> implements FolderItemPrefs {
	public static final String SCHEME = "folder";
	private String subtitle;

	private FolderItem(String id, BrowsableItem parent, MediaFile file) {
		super(id, parent, file);
	}

	static FolderItem create(String id, BrowsableItem parent, MediaFile file, DefaultMediaLib lib) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				FolderItem f = (FolderItem) i;
				if (DEBUG && !parent.equals(f.getParent())) throw new AssertionError();
				if (DEBUG && !file.equals(f.getFile())) throw new AssertionError();
				return f;
			} else {
				return new FolderItem(id, parent, file);
			}
		}
	}

	static FolderItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int idx = id.lastIndexOf('/');
		if ((idx == -1) || (idx == (id.length() - 1))) return null;

		String name = id.substring(idx + 1);
		FolderItem parent = (FolderItem) lib.getItem(id.substring(0, idx));
		if (parent == null) return null;

		MediaFile folder = parent.getFile().getChild(name);
		return (folder != null) ? FolderItem.create(id, parent, folder, lib) : null;
	}

	@NonNull
	@Override
	public String getSubtitle() {
		if (subtitle == null) {
			int files = 0;
			int folders = 0;
			for (Item i : getUnsortedChildren()) {
				if (i instanceof PlayableItem) files++;
				else folders++;
			}
			subtitle = getLib().getContext().getResources().getString(R.string.folder_subtitle, files, folders);
		}

		return subtitle;
	}

	@Override
	public FolderItemPrefs getPrefs() {
		return this;
	}

	@Override
	void buildCompleteDescription(MediaDescriptionCompat.Builder b) {
		super.buildCompleteDescription(b);
		MediaFile file = getFile();
		MediaFile cover = file.getChild("cover.jpg");

		if (cover != null) {
			b.setIconUri(cover.getUri());
		} else if ((cover = file.getChild("folder.jpg")) != null) {
			b.setIconUri(cover.getUri());
		}
	}

	public List<Item> listChildren() {
		List<MediaFile> ls = getFile().ls();
		if (ls.isEmpty()) return Collections.emptyList();

		String id = getId();
		DefaultMediaLib lib = getLib();
		List<Item> children = new ArrayList<>(ls.size());
		StringBuilder fileBuf = null;
		StringBuilder folderBuf = null;
		StringBuilder cueBuf = null;
		StringBuilder m3uBuf = null;
		int fileBufLen = 0;
		int folderBufLen = 0;
		int cueBufLen = 0;
		int m3uBufLen = 0;

		for (MediaFile f : ls) {
			String name = f.getName();
			if (name.startsWith(".")) continue;

			Item i;

			if (!f.isDirectory()) {
				String ext = FileUtils.getFileExtension(name);
				if (ext == null) continue;

				String mime = FileUtils.getMimeTypeFromExtension(ext);
				if (!isSupportedFile(ext, mime)) continue;

				if (CueItem.isCueFile(name)) {
					if (cueBuf == null) {
						cueBuf = new StringBuilder(id.length() + name.length() + 1);
						cueBuf.append(CueItem.SCHEME).append(id, SCHEME.length(), id.length()).append('/');
						cueBufLen = cueBuf.length();
					} else {
						cueBuf.setLength(cueBufLen);
					}

					cueBuf.append(name);
					i = CueItem.create(cueBuf.toString(), this, getFile(), f, lib);
				} else if (M3uItem.isM3uFile(name)) {
					if (m3uBuf == null) {
						m3uBuf = new StringBuilder(id.length() + name.length() + 1);
						m3uBuf.append(M3uItem.SCHEME).append(id, SCHEME.length(), id.length()).append('/');
						m3uBufLen = m3uBuf.length();
					} else {
						m3uBuf.setLength(m3uBufLen);
					}

					m3uBuf.append(name);
					i = M3uItem.create(m3uBuf.toString(), this, getFile(), f, lib);
				} else {
					if (fileBuf == null) {
						fileBuf = TextUtils.getSharedStringBuilder();
						fileBuf.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length()).append('/');
						fileBufLen = fileBuf.length();
					} else {
						fileBuf.setLength(fileBufLen);
					}

					fileBuf.append(name);
					i = FileItem.create(fileBuf.toString(), this, f, lib, Utils.isVideoMimeType(mime));
				}
			} else {
				if (folderBuf == null) {
					folderBuf = new StringBuilder(id.length() + name.length() + 1);
					folderBuf.append(id).append('/');
					folderBufLen = folderBuf.length();
				} else {
					folderBuf.setLength(folderBufLen);
				}

				folderBuf.append(name);
				i = FolderItem.create(folderBuf.toString(), this, f, lib);
			}

			children.add(i);
		}

		return children;
	}

	@Override
	public void clearCache() {
		super.clearCache();
		subtitle = null;
	}

	@Override
	public void updateTitles() {
		super.updateTitles();
		subtitle = null;
	}

	private static boolean isSupportedFile(String ext, String mime) {
		if ((mime != null) && (mime.startsWith("audio/") || Utils.isVideoMimeType(mime))) {
			return true;
		} else {
			switch (ext) {
				case "ac3":
				case "cue":
				case "m3u":
				case "m3u8":
				case "flac":
				case "ogg":
				case "opus":
					return true;
				default:
					return false;
			}
		}
	}
}
