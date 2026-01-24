package me.aap.utils.vfs.smb;

import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY;

import androidx.annotation.NonNull;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.protocol.commons.EnumWithValue;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
class SmbFolder extends SmbResource implements VirtualFolder {

	SmbFolder(@NonNull SmbRoot root, @NonNull String path) {
		super(root, path);
	}

	SmbFolder(@NonNull SmbRoot root, @NonNull String path, VirtualFolder parent) {
		super(root, path, parent);
	}

	@Override
	public FutureSupplier<List<VirtualResource>> getChildren() {
		SmbRoot root = getRoot();
		return root.useShare(s -> {
			List<FileIdBothDirectoryInformation> info = s.list(smbPath());
			List<VirtualResource> ls = new ArrayList<>(info.size());

			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				tb.append(getPath()).append('/');
				int len = tb.length();

				for (FileIdBothDirectoryInformation i : info) {
					String name = i.getFileName();
					if (name.equals(".") || name.equals("..")) continue;

					tb.setLength(len);
					String p = tb.append(name).toString();

					if (EnumWithValue.EnumUtils.isSet(i.getFileAttributes(), FILE_ATTRIBUTE_DIRECTORY)) {
						ls.add(new SmbFolder(root, p, this));
					} else {
						ls.add(new SmbFile(root, p, this));
					}
				}
			}

			return ls;
		});
	}

	@Override
	public boolean canDelete() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		return getRoot().useShare(s -> {
			try {
				s.rmdir(getPath(), true);
				return true;
			} catch (Exception ex) {
				Log.e(ex, "Failed to delete folder ", getPath());
				return false;
			}
		});
	}
}
