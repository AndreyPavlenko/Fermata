package me.aap.utils.vfs;

import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Function;

/**
 * @author Andrey Pavlenko
 */
public interface VirtualFolder extends VirtualResource {

	FutureSupplier<List<VirtualResource>> getChildren();

	default Filter filterChildren() {
		return new BasicFilter(this);
	}

	default FutureSupplier<VirtualResource> getChild(CharSequence name) {
		return getChildren().then(children -> {
			for (VirtualResource c : children) {
				if (name.equals(c.getName())) return completed(c);
			}
			return completedNull();
		});
	}

	default FutureSupplier<VirtualFile> createFile(CharSequence name) {
		return Completed.failed(new UnsupportedOperationException());
	}

	default FutureSupplier<VirtualFolder> createFolder(CharSequence name) {
		return Completed.failed(new UnsupportedOperationException());
	}

	default FutureSupplier<VirtualFile> createTempFile(CharSequence prefix, CharSequence suffix) {
		String name = prefix.toString() + UUID.randomUUID() + suffix;
		return getChild(name).then(f -> (f == null) ? createFile(name) :
				f.exists().then(e -> e ? createTempFile(prefix, suffix) : createFile(name)));
	}

	default boolean isFile() {
		return false;
	}

	default boolean isFolder() {
		return true;
	}

	interface Filter {

		FutureSupplier<List<VirtualResource>> apply();

		Filter starts(String prefix);

		Filter ends(String suffix);

		Filter startsEnds(String prefix, String suffix);

		Filter and();

		Filter or();

		Filter not();
	}

	class BasicFilter implements Filter {
		private final VirtualFolder folder;
		private Function<String, Boolean> func;
		private boolean and = true;
		private boolean not = false;

		public BasicFilter(VirtualFolder folder) {this.folder = folder;}

		@Override
		public FutureSupplier<List<VirtualResource>> apply() {
			if (func == null) return folder.getChildren();
			return folder.getChildren().map(this::apply);
		}

		protected List<VirtualResource> apply(List<VirtualResource> children) {
			if (children.isEmpty()) return emptyList();
			var list = new ArrayList<VirtualResource>();
			for (var c : children)
				if (func.apply(c.getName())) list.add(c);
			return list;
		}

		@Override
		public Filter starts(String prefix) {
			return add(not ? n -> !n.startsWith(prefix) : n -> n.startsWith(prefix));
		}

		@Override
		public Filter ends(String suffix) {
			return add(not ? n -> !n.endsWith(suffix) : n -> n.endsWith(suffix));
		}

		@Override
		public Filter startsEnds(String prefix, String suffix) {
			return add(not ? n -> !n.startsWith(prefix) && !n.endsWith(suffix) :
					n -> n.startsWith(prefix) && n.endsWith(suffix));
		}

		@Override
		public Filter and() {
			and = true;
			return this;
		}

		@Override
		public Filter or() {
			and = false;
			return this;
		}

		@Override
		public Filter not() {
			not = true;
			return this;
		}

		private Filter add(Function<String, Boolean> f) {
			var func = this.func;
			not = false;
			if (func == null) {
				this.func = f;
			} else if (and) {
				this.func = n -> func.apply(n) && f.apply(n);
			} else {
				this.func = n -> func.apply(n) || f.apply(n);
			}
			return this;
		}
	}
}
