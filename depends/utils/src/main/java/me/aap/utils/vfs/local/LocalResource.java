package me.aap.utils.vfs.local;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.os.OsUtils.isRmAvailable;
import static me.aap.utils.os.OsUtils.isSuAvailable;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.os.OsUtils;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
abstract class LocalResource implements VirtualResource {
	@NonNull
	final File file;
	private FutureSupplier<VirtualFolder> parent;

	LocalResource(@NonNull File file) {
		this.file = file;
	}

	public LocalResource(@NonNull File file, VirtualFolder parent) {
		this.file = file;
		this.parent = completed(parent);
	}

	@NonNull
	@Override
	public LocalFileSystem getVirtualFileSystem() {
		return LocalFileSystem.getInstance();
	}

	@NonNull
	@Override
	public String getName() {
		String name = file.getName();
		return name.isEmpty() ? file.getPath() : name;
	}

	@NonNull
	@Override
	public Rid getRid() {
		return Rid.create(file);
	}

	@Override
	public boolean isLocalFile() {
		return true;
	}

	@NonNull
	@Override
	public File getLocalFile() {
		return file;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> exists() {
		return completed(file.exists());
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> create() {
		try {
			if (isFile()) return completed(file.createNewFile());
			if (file.mkdirs()) return completed(true);
			if (file.isDirectory()) return completed(false);
			return failed(new IOException("Failed to create folder: " + file));
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	@Override
	public boolean canDelete() {
		if (isSuAvailable() && isRmAvailable()) return true;
		return getLocalFile().canWrite();
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		if (!file.exists()) return completed(false);

		if (isFile()) {
			getVirtualFileSystem().closeCachedChannels(file);
			if (file.delete()) return completed(true);
			return OsUtils.su(15000, "rm '", file.getAbsolutePath(), "'").map(r -> r == 0);
		}

		try {
			FileUtils.delete(file);
			return completed(true);
		} catch (Throwable ex) {
			return OsUtils.su(30000, "rm -rf '", file.getAbsolutePath(), "'").map(r -> r == 0);
		}
	}

	@Override
	public FutureSupplier<Long> getLastModified() {
		return Completed.completed(file.lastModified());
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		if (parent == null) {
			File p = file.getParentFile();
			parent = (p != null) && p.canRead() ? completed(new LocalFolder(p))
					: Completed.completedNull();
		}

		return parent;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return file.equals(((VirtualResource) o).getLocalFile());
	}

	@Override
	public int hashCode() {
		return file.hashCode();
	}

	@NonNull
	@Override
	public String toString() {
		return file.toString();
	}
}
