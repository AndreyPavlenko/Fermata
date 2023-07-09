package me.aap.fermata.media.lib;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.FolderItemPrefs;
import me.aap.fermata.util.Utils;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.content.ContentFileSystem;
import me.aap.utils.vfs.local.LocalFileSystem;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

/**
 * @author Andrey Pavlenko
 */
public class FolderItem extends BrowsableItemBase implements FolderItemPrefs {
	public static final String SCHEME = "folder";
	private volatile FutureSupplier<Uri> iconUri;

	private FolderItem(String id, BrowsableItem parent, VirtualFolder file) {
		super(id, parent, file);
	}

	static FolderItem create(String id, BrowsableItem parent, VirtualFolder file, DefaultMediaLib lib) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				FolderItem f = (FolderItem) i;
				if (BuildConfig.D && !parent.equals(f.getParent())) throw new AssertionError();
				if (BuildConfig.D && !file.equals(f.getResource())) throw new AssertionError();
				return f;
			} else {
				return new FolderItem(id, parent, file);
			}
		}
	}

	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int idx = id.lastIndexOf('/');
		if (idx == (id.length() - 1)) return completedNull();

		if (idx == -1) {
			return lib.getFolders().getUnsortedChildren().map(children -> {
				String name = id.substring(SCHEME.length() + 1);
				for (Item i : children) {
					if (name.equals(i.getName())) return i;
				}
				return null;
			});
		}

		String name = id.substring(idx + 1);

		return lib.getItem(id.substring(0, idx)).then(i -> {
			FolderItem parent = (FolderItem) i;
			if (parent == null) return completedNull();

			return parent.getResource().getChild(name).map(folder -> (folder instanceof VirtualFolder)
					? FolderItem.create(id, parent, (VirtualFolder) folder, lib) : null
			);
		});
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	@Override
	public VirtualFolder getResource() {
		return (VirtualFolder) super.getResource();
	}

	@NonNull
	@Override
	public FolderItemPrefs getPrefs() {
		return this;
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		String sub = super.buildSubtitle(children);
		if (getParent() instanceof MediaLib.Folders) {
			VirtualResource f = getResource();
			VirtualFileSystem fs = f.getVirtualFileSystem();
			if (!(fs instanceof LocalFileSystem) && !(fs instanceof ContentFileSystem)) {
				Rid rid = f.getRid();
				return sub + " (" + rid.getScheme() + "://" + rid.getAuthority() + ')';
			}
		}
		return sub;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getResource().getChildren().map(this::ls);
	}

	private List<Item> ls(List<VirtualResource> ls) {
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

		for (VirtualResource f : ls) {
			String name = f.getName();
			if (name.startsWith(".")) continue;

			if (isIcon(name)) {
				iconUri = completed(f.getRid().toAndroidUri());
				continue;
			}

			Item i;

			if (!f.isFolder()) {
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
					i = CueItem.create(cueBuf.toString(), this, (VirtualFile) f, lib);
				} else if (M3uItem.isM3uFile(name)) {
					if (m3uBuf == null) {
						m3uBuf = new StringBuilder(id.length() + name.length() + 1);
						m3uBuf.append(M3uItem.SCHEME).append(id, SCHEME.length(), id.length()).append('/');
						m3uBufLen = m3uBuf.length();
					} else {
						m3uBuf.setLength(m3uBufLen);
					}

					m3uBuf.append(name);
					i = M3uItem.create(m3uBuf.toString(), this, (VirtualFile) f, lib);
				} else {
					if (fileBuf == null) {
						fileBuf = new StringBuilder(id.length() + name.length() + 64);
						fileBuf.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length()).append('/');
						fileBufLen = fileBuf.length();
					} else {
						fileBuf.setLength(fileBufLen);
					}

					fileBuf.append(name);
					i = FileItem.create(fileBuf.toString(), this, f, lib, Utils.isVideoMimeType(mime));
				}
			} else if (f instanceof VirtualFolder) {
				if (folderBuf == null) {
					folderBuf = new StringBuilder(id.length() + name.length() + 1);
					folderBuf.append(id).append('/');
					folderBufLen = folderBuf.length();
				} else {
					folderBuf.setLength(folderBufLen);
				}

				folderBuf.append(name);
				i = FolderItem.create(folderBuf.toString(), this, (VirtualFolder) f, lib);
			} else {
				continue;
			}

			children.add(i);
		}

		return children;
	}

	@Override
	protected String getChildrenIdPattern() {
		return '%' + getId().substring(SCHEME.length()) + "/%";
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		if (iconUri == null) {
			return getResource().getChild("cover.jpg").then(cover -> {
				if (cover != null) return iconUri = completed(cover.getRid().toAndroidUri());

				return getResource().getChild("folder.jpg").then(folder -> {
					if (folder != null) return iconUri = completed(folder.getRid().toAndroidUri());
					else return iconUri = completedNull();
				});
			});
		}

		return iconUri;
	}

	private boolean isIcon(String name) {
		return "cover.jpg".equals(name) || "folder.jpg".equals(name);
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
