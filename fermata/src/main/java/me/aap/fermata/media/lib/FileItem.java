package me.aap.fermata.media.lib;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.fermata.util.Utils.isVideoFile;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.security.SecurityUtils.md5;
import static me.aap.utils.text.TextUtils.appendHexString;

/**
 * @author Andrey Pavlenko
 */
@SuppressLint("InlinedApi")
public class FileItem extends PlayableItemBase {
	public static final String SCHEME = "file";
	private final boolean isVideo;

	private FileItem(String id, BrowsableItem parent, VirtualResource file, boolean isVideo) {
		super(id, parent, file);
		this.isVideo = isVideo;
	}

	static FileItem create(String id, BrowsableItem parent, VirtualResource file, DefaultMediaLib lib,
												 boolean isVideo) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				FileItem f = (FileItem) i;
				if (BuildConfig.D && !parent.equals(f.getParent())) throw new AssertionError();

				if (!file.equals(f.getResource())) {
					StringBuilder sb = new StringBuilder(id.length() + 33);
					sb.append(id).append('_');
					appendHexString(sb, md5(file.getRid().toString()));
					return new FileItem(sb.toString(), parent, file, isVideo);
				}

				return f;
			} else {
				return new FileItem(id, parent, file, isVideo);
			}
		}
	}

	@NonNull
	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int idx = id.lastIndexOf('/');
		if ((idx == -1) || (idx == (id.length() - 1))) return completedNull();

		String name = id.substring(idx + 1);
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(FolderItem.SCHEME).append(id, SCHEME.length(), idx);

		return lib.getItem(tb.releaseString()).then(i -> {
			FolderItem parent = (FolderItem) i;
			if (parent == null) return completedNull();

			return parent.getResource().getChild(name).map(file -> (file != null) ?
					create(id, parent, file, lib, isVideoFile(file.getName())) : null);
		});
	}

	@Override
	public boolean isVideo() {
		return isVideo;
	}

	@Override
	public String getOrigId() {
		String id = getId();
		if (id.startsWith(SCHEME)) return id;
		return id.substring(id.indexOf(SCHEME));
	}
}
