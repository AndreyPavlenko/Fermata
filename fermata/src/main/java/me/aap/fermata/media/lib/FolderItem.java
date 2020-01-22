package me.aap.fermata.media.lib;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FolderItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.fermata.util.Utils;

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
			for (Item i : getChildren(null)) {
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
	public MediaDescriptionCompat.Builder getMediaDescriptionBuilder() {
		MediaFile file = getFile();
		MediaDescriptionCompat.Builder b = super.getMediaDescriptionBuilder();

		MediaFile cover = file.getChild("cover.jpg");

		if (cover != null) {
			b.setIconBitmap(getBitmap(cover));
		} else if ((cover = file.getChild("folder.jpg")) != null) {
			b.setIconBitmap(getBitmap(cover));
		}

		return b;
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
		int fileBufLen = 0;
		int folderBufLen = 0;
		int cueBufLen = 0;

		for (MediaFile f : ls) {
			String name = f.getName();
			if (name.startsWith(".")) continue;

			Item i;

			if (!f.isDirectory()) {
				String ext = Utils.getFileExtension(name);
				if (ext == null) continue;

				String mime = Utils.getMimeTypeFromExtension(ext);
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
				} else {
					if (fileBuf == null) {
						fileBuf = Utils.getSharedStringBuilder();
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
				case "flac":
				case "ogg":
				case "opus":
					return true;
				default:
					return false;
			}
		}
	}

	private static Bitmap getBitmap(MediaFile f) {
		ParcelFileDescriptor fd = null;
		try {
			fd = FermataApplication.get().getContentResolver().openFileDescriptor(f.getUri(), "r");
			return (fd != null) ? BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor()) : null;
		} catch (FileNotFoundException ignore) {
		} catch (Exception ex) {
			Log.d("SafFolderItem", "Failed to read bitmap", ex);
		} finally {
			Utils.close(fd);
		}
		return null;
	}
}
