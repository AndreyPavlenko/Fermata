package me.aap.utils.vfs.generic;

import androidx.annotation.NonNull;

import java.util.Objects;

import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class GenericResource implements VirtualResource {
	private final Rid rid;

	public GenericResource(Rid rid) {
		this.rid = rid;
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return GenericFileSystem.getInstance();
	}

	@NonNull
	@Override
	public String getName() {
		return getRid().toString();
	}

	@NonNull
	@Override
	public Rid getRid() {
		return rid;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GenericResource that = (GenericResource) o;
		return Objects.equals(rid, that.rid);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rid);
	}
}