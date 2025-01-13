package me.aap.fermata.addon.felex.dict;

import static java.util.Collections.emptyList;
import static me.aap.fermata.addon.felex.dict.DictInfo.BATCH_SIZE_DEFAULT;
import static me.aap.fermata.addon.felex.dict.DictInfo.BATCH_TYPE_MIXED;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.collection.CollectionUtils.contains;
import static me.aap.utils.misc.Assert.assertMainThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.felex.FelexAddon;
import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.collection.NaturalOrderComparator;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
import me.aap.utils.os.OsUtils;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class DictMgr extends BasicEventBroadcaster<DictMgr.ProgressChangeListener>
		implements Comparable<DictMgr> {
	public static final String DICT_EXT = ".fxd";
	public static final String CACHE_EXT = ".fxc";
	static final PromiseQueue queue = new PromiseQueue();
	private static final DictMgr instance = new DictMgr(null, null);
	@Nullable
	private final DictMgr parent;
	@Nullable
	private final VirtualFolder folder;
	private FutureSupplier<List<DictMgr>> children;
	private FutureSupplier<List<Dict>> dictionaries;

	static {
		OsUtils.addShutdownHook(() -> instance.reset().get());
	}

	private DictMgr(@Nullable DictMgr parent, @Nullable VirtualFolder folder) {
		this.parent = parent;
		this.folder = folder;
	}

	public static DictMgr get() {
		return instance;
	}

	public String getName() {
		return folder == null ? "" : folder.getName();
	}

	public String getPath() {
		var path = (parent == null) ? "" : parent.getPath();
		return path.isEmpty() ? getName() : path + "/" + getName();
	}

	@Nullable
	public DictMgr getParent() {
		return parent;
	}

	public FutureSupplier<List<DictMgr>> getChildren() {
		assertMainThread();
		if (children == null) {
			children = getFolder().then(VirtualFolder::getChildren).map(files -> {
				List<DictMgr> list = new ArrayList<>();
				for (VirtualResource c : files) {
					if (c instanceof VirtualFolder f) list.add(new DictMgr(this, f));
				}
				Collections.sort(list);
				return list;
			}).main().onCompletion((d, err) -> {
				if (err != null) {
					Log.e(err, "Failed to load children");
					children = null;
				} else {
					Log.i("Loaded children: ", d);
					children = completed(d);
				}
			});
		}
		return children.fork();
	}

	public FutureSupplier<DictMgr> getChild(String path) {
		if (path.indexOf('/') < 0) {
			return getChildren().map(list -> CollectionUtils.find(list, d -> d.getName().equals(path)));
		}

		var parts = path.split("/");
		FutureSupplier<DictMgr> f = parts[0].isEmpty() ? completed(this) : getChild(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			var name = parts[i];
			if (!name.isEmpty()) f = f.then(d -> d == null ? completedNull() : d.getChild(name));
		}
		return f;
	}

	public FutureSupplier<List<Dict>> getDictionaries() {
		assertMainThread();
		if (dictionaries == null) {
			dictionaries = load().then(d -> {
				if (!d.isEmpty() || (parent != null)) return completed(d);
				return createExample().then(f -> Dict.create(this, f)).map(Collections::singletonList)
						.ifFail(err -> {
							Log.e(err, "Failed to create example");
							return emptyList();
						});
			}).main().onCompletion((d, err) -> {
				if (err != null) {
					Log.e(err, "Failed to load dictionaries");
					dictionaries = null;
				} else {
					Log.i("Loaded dictionaries: ", d);
					dictionaries = completed(d);
				}
			});
		}
		return dictionaries.fork();
	}

	public FutureSupplier<Dict> getDictionary(String path) {
		int idx = path.lastIndexOf('/');
		String name;
		FutureSupplier<DictMgr> getMgr;
		if (idx < 0) {
			name = path;
			getMgr = completed(this);
		} else {
			name = path.substring(idx + 1);
			getMgr = getChild(path.substring(0, idx));
		}
		return getMgr.then(mgr -> mgr == null ? completedEmptyList() : mgr.getDictionaries()).map(
				dicts -> CollectionUtils.find(dicts, d -> d.getName().equals(name)));
	}

	public FutureSupplier<Dict> createDictionary(String name, Locale srcLang, Locale targetLang,
																							 String ackPhrase, String skipPhrase) {
		return createDictionary(
				new DictInfo(name, srcLang, targetLang, ackPhrase, skipPhrase, null, BATCH_SIZE_DEFAULT,
						BATCH_TYPE_MIXED));
	}

	public FutureSupplier<Dict> createDictionary(DictInfo info) {
		assertMainThread();
		int idx = info.getPath().lastIndexOf('/');
		FutureSupplier<DictMgr> getMgr = completed(this);

		if (idx > 0) {
			var path = info.getPath().substring(0, idx);
			for (var name : path.split("/")) {
				getMgr = getMgr.then(mgr -> mgr.getChild(name).then(c -> (c == null) ? mgr.getFolder()
						.then(folder -> folder.createFolder(name))
						.main().then(newFolder -> {
							if (mgr.children == null) {
								return mgr.getChild(name).then(child ->
										child == null ?
												failed(new IOException("Child not found: " + name)) :
												completed(child)
								);
							} else {
								return mgr.children.map(list -> {
									var newMgr = new DictMgr(mgr, newFolder);
									list.add(newMgr);
									Collections.sort(list);
									return newMgr;
								});
							}
						}) : completed(c)));
			}
		}

		return getMgr.then(mgr -> mgr.getDictionaries().then(dicts -> {
					String name = info.getName();

					if (contains(dicts, d -> d.getName().equalsIgnoreCase(name))) {
						throw new IllegalStateException("Dictionary already exists: " + name);
					}

					String fileName = name + DICT_EXT;
					return mgr.getFolder().then(dir -> dir.getChild(fileName).then(
									c -> (c == null) ? dir.createFile(fileName) : dir.createTempFile(name + '-',
											DICT_EXT)))
							.then(f -> Dict.create(mgr, f, info)).main().onSuccess(d -> {
								List<Dict> newDicts = new ArrayList<>(dicts.size() + 1);
								newDicts.addAll(dicts);
								newDicts.add(d);
								Collections.sort(newDicts);
								mgr.dictionaries = Completed.completed(newDicts);
							});
				})
		).main();
	}

	public FutureSupplier<Integer> deleteDictionary(Dict d) {
		assertMainThread();
		return getDictionaries().main().then(dicts -> {
			int idx = dicts.indexOf(d);
			if (idx < 0) return completed(-1);
			Log.i("Deleting dictionary ", d);
			dicts.remove(idx);
			dictionaries = completed(dicts);
			return d.close()
					.thenIgnoreResult(d.getDictFile()::delete)
					.thenIgnoreResult(() -> d.getCacheFile(false))
					.then(c -> (c == null) ? completed(true) : c.delete())
					.map(cd -> idx);
		}).main();
	}

	public FutureSupplier<?> delete() {
		assertMainThread();
		if (parent == null) return failed(new UnsupportedOperationException());
		Log.i("Deleting DictMgr ", getPath());
		return getChildren()
				.then(list -> Async.forEach(DictMgr::delete, new ArrayList<>(list)))
				.main().thenIgnoreResult(this::getDictionaries)
				.then(list -> Async.forEach(this::deleteDictionary, new ArrayList<>(list)))
				.main().thenIgnoreResult(this::reset)
				.thenIgnoreResult(() -> getFolder().then(VirtualFolder::delete))
				.thenIgnoreResult(FelexAddon.get()::getCacheFolder)
				.then(cache -> cache.getChild(getPath()))
				.then(c -> (c == null) ? completed(true) : c.delete())
				.main().thenIgnoreResult(parent::getChildren)
				.onSuccess(list ->
						CollectionUtils.remove(list, c -> c.folder != null && c.folder.equals(folder)));
	}

	public FutureSupplier<?> reset() {
		assertMainThread();
		FutureSupplier<Void> f = completedVoid();
		if (dictionaries != null) {
			f = dictionaries.then(dicts -> Async.forEach(Dict::close, dicts)).main()
					.onCompletion((r, err) -> {
						dictionaries = null;
						if (err != null) Log.e(err, "Failed to close dictionaries");
					});
		}
		if (children != null) {
			f = f.thenIgnoreResult(() -> children.then(list -> Async.forEach(DictMgr::reset, list)))
					.main().onCompletion((r, err) -> {
						children = null;
						if (err != null) Log.e(err, "Failed to reset children");
					});
		}
		return f;
	}

	private FutureSupplier<VirtualFolder> getFolder() {
		return folder == null ? FelexAddon.get().getDictFolder() : completed(folder);
	}

	private <T> FutureSupplier<T> enqueue(CheckedSupplier<T, Throwable> task) {
		return queue.enqueue(task);
	}

	private FutureSupplier<List<Dict>> load() {
		return getFolder()
				.then(folder -> enqueue(folder::getChildren)).map(FutureSupplier::peek)
				.then(files -> {
					ArrayList<Dict> list = new ArrayList<>(files.size());
					return Async.forEach(f -> (f instanceof VirtualFile) && (f.getName().endsWith(DICT_EXT)) ?
							Dict.create(this, (VirtualFile) f).onSuccess(list::add).ifFail(err -> {
								Log.e(err, "Failed to load dictionary: ", f);
								return null;
							}) : completedVoid(), files).map(v -> {
						list.trimToSize();
						Collections.sort(list);
						return list;
					});
				});
	}

	private FutureSupplier<VirtualFile> createExample() {
		return getFolder().then(f -> f.createFile("Example" + DICT_EXT)).map(f -> {
			try (InputStream in = openExampleAsset();
					 OutputStream out = f.getOutputStream().asOutputStream()) {
				FileUtils.copy(in, out);
			}
			return f;
		});
	}

	private static InputStream openExampleAsset() throws IOException {
		try {
			String lang = Locale.getDefault().getLanguage().toLowerCase();
			return App.get().getAssets().open("Example_" + lang + DICT_EXT);
		} catch (IOException ex) {
			return App.get().getAssets().open("Example" + DICT_EXT);
		}
	}

	PromiseQueue queue() {
		return queue;
	}

	@Override
	public int compareTo(DictMgr o) {
		return NaturalOrderComparator.compareNatural(getName(), o.getName());
	}

	@NonNull
	@Override
	public String toString() {
		return getName();
	}

	public interface ProgressChangeListener {
		void onProgressChanged(Dict d, Word w);
	}
}
