package me.aap.utils.vfs;

import androidx.annotation.NonNull;

import java.util.Objects;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

/**
 * @author Andrey Pavlenko
 */
public class NetResourceBase<R extends VirtualFolder> implements VirtualResource {
	@NonNull
	private final R root;
	@NonNull
	private final String path;
	private Rid rid;
	private FutureSupplier<VirtualFolder> parent;
	private FutureSupplier<Long> lastModified;

	protected NetResourceBase(@NonNull R root, @NonNull String path) {
		this.root = root;
		this.path = path;
	}

	public NetResourceBase(@NonNull R root, @NonNull String path, VirtualFolder parent) {
		this.root = root;
		this.path = path;
		this.parent = completed(parent);
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return getRoot().getVirtualFileSystem();
	}

	@NonNull
	@Override
	public String getName() {
		if (path.equals("/")) return "/";
		int idx = path.lastIndexOf('/');
		return (idx != -1) ? path.substring(idx + 1) : path;
	}

	@NonNull
	@Override
	public Rid getRid() {
		if (rid == null) {
			Rid r = getRoot().getRid();
			rid = Rid.create(r.getScheme(), r.getUserInfo(), r.getHost(), r.getPort(), getPath());
		}
		return rid;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		FutureSupplier<VirtualFolder> p = parent;
		if (p != null) return p;

		R root = getRoot();
		int idx = path.lastIndexOf('/');
		if (idx == 0) return parent = ("/".equals(path) ? completedNull() : completed(root));

		String pp = path.substring(0, idx);
		if (pp.equals(root.getRid().getPath())) return parent = completed(root);

		NetFileSystemBase fs = (NetFileSystemBase) root.getVirtualFileSystem();
		return fs.createResource(root, pp).then(r ->
				parent = (r instanceof VirtualFolder) ? completed((VirtualFolder) r) : completedNull()
		);
	}

	@Override
	public FutureSupplier<Long> getLastModified() {
		return (lastModified != null) ? lastModified :
				(lastModified = loadLastModified().onSuccess(lm -> lastModified = completed(lm)));
	}

	protected FutureSupplier<Long> loadLastModified() {
		return completed(0L);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NetResourceBase<?> that = (NetResourceBase<?>) o;
		return getRoot().equals(that.getRoot()) && path.equals(that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRoot(), path);
	}

	@NonNull
	@Override
	public String toString() {
		return getRid().toString();
	}

	@NonNull
	protected R getRoot() {
		return root;
	}

	@NonNull
	protected String getPath() {
		return path;
	}
}
