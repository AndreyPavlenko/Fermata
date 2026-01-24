package me.aap.utils.vfs.sftp;

import androidx.annotation.NonNull;

import com.jcraft.jsch.ChannelSftp.LsEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
class SftpFolder extends SftpResource implements VirtualFolder {

	SftpFolder(@NonNull SftpRoot root, @NonNull String path) {
		super(root, path);
	}

	SftpFolder(@NonNull SftpRoot root, @NonNull String path, VirtualFolder parent) {
		super(root, path, parent);
	}

	@Override
	public FutureSupplier<List<VirtualResource>> getChildren() {
		SftpRoot root = getRoot();
		return root.useChannel(ch -> {
			@SuppressWarnings("unchecked") List<LsEntry> ls = ch.ls(getPath());
			if (ls.isEmpty()) return Collections.emptyList();

			List<VirtualResource> children = new ArrayList<>(ls.size());

			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				tb.append(getPath()).append('/');
				int len = tb.length();

				for (LsEntry e : ls) {
					String name = e.getFilename();
					if (name.equals(".") || name.equals("..")) continue;
					tb.setLength(len);
					String p = tb.append(name).toString();
					if (e.getAttrs().isDir()) children.add(new SftpFolder(root, p, this));
					else children.add(new SftpFile(root, p, this));
				}
			}

			return children;
		});
	}

	@Override
	public boolean canDelete() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		return getChildren().then(ls -> Async.forEach(VirtualResource::delete, ls).then(v ->
				getRoot().useChannel(ch -> {
					try {
						ch.rmdir(getPath());
						return true;
					} catch (Exception ex) {
						Log.e(ex, "Failed to delete directory ", getPath());
						return false;
					}
				}))
		);
	}
}
