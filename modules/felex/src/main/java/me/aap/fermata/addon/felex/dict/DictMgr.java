package me.aap.fermata.addon.felex.dict;

import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.contains;
import static me.aap.utils.misc.Assert.assertMainThread;

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
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
import me.aap.utils.os.OsUtils;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class DictMgr {
	public static final String DICT_EXT = ".fxd";
	public static final String CACHE_EXT = ".fxc";
	private static final DictMgr instance = new DictMgr();
	final PromiseQueue queue = new PromiseQueue();
	private FutureSupplier<List<Dict>> dictionaries;

	private DictMgr() {
		OsUtils.addShutdownHook(() -> reset().get());
	}

	public static DictMgr get() {
		return instance;
	}

	public FutureSupplier<List<Dict>> getDictionaries() {
		assertMainThread();
		if (dictionaries == null) {
			dictionaries = load().then(d -> {
				if (!d.isEmpty()) return completed(d);
				return createExample().then(f -> Dict.create(this, f))
						.map(Collections::singletonList).ifFail(err -> {
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

	public FutureSupplier<Dict> getDictionary(String name) {
		return getDictionaries().map(dicts -> CollectionUtils
				.find(dicts, d -> d.getName().equals(name)));
	}

	public FutureSupplier<Dict> createDictionary(String name, Locale srcLang, Locale targetLang) {
		return createDictionary(new DictInfo(name, srcLang, targetLang));
	}

	public FutureSupplier<Dict> createDictionary(DictInfo info) {
		assertMainThread();
		return getDictionaries().then(dicts -> {
			String name = info.getName();

			if (contains(dicts, d -> d.getName().equalsIgnoreCase(name))) {
				throw new IllegalStateException("Dictionary already exists: " + name);
			}

			String fileName = name + DICT_EXT;
			return FelexAddon.get().getDictFolder().then(dir -> dir.getChild(fileName)
							.then(c -> (c == null) ? dir.createFile(fileName)
									: dir.createTempFile(name + '-', DICT_EXT)))
					.then(f -> Dict.create(this, f, info)).main()
					.onSuccess(d -> {
						List<Dict> newDicts = new ArrayList<>(dicts.size() + 1);
						newDicts.addAll(dicts);
						newDicts.add(d);
						Collections.sort(newDicts);
						dictionaries = Completed.completed(newDicts);
					});
		}).main();
	}

	public FutureSupplier<Integer> deleteDictionary(Dict d) {
		assertMainThread();
		return getDictionaries().main().then(dicts -> {
			int idx = dicts.indexOf(d);
			if (idx < 0) return completed(-1);
			Log.i("Deleting dictionary ", d);
			dicts.remove(idx);
			dictionaries = completed(dicts);
			return d.close().then(v -> d.getDictFile().delete()
							.then(fd -> d.getCacheFile(false)
									.then(c -> (c == null) ? completed(true) : c.delete())))
					.map(cd -> idx);
		}).main();
	}

	public FutureSupplier<Void> reset() {
		assertMainThread();
		if (dictionaries == null) return completedVoid();
		return dictionaries.then(dicts -> Async.forEach(Dict::close, dicts))
				.main().onCompletion((r, err) -> {
					dictionaries = null;
					if (err != null) Log.e(err, "Failed to close dictionaries");
				});
	}

	private <T> FutureSupplier<T> enqueue(CheckedSupplier<T, Throwable> task) {
		return queue.enqueue(task);
	}

	private FutureSupplier<List<Dict>> load() {
		return FelexAddon.get().getDictFolder().then(folder ->
				enqueue(folder::getChildren).map(FutureSupplier::peek).then(files -> {
					ArrayList<Dict> list = new ArrayList<>(files.size());
					return Async.forEach(f -> (f instanceof VirtualFile) && (f.getName().endsWith(DICT_EXT))
									? Dict.create(this, (VirtualFile) f)
									.onSuccess(list::add)
									.ifFail(err -> {
										Log.e(err, "Failed to load dictionary: ", f);
										return null;
									}) : completedVoid(), files)
							.map(v -> {
								list.trimToSize();
								Collections.sort(list);
								return list;
							});
				}));
	}

	private FutureSupplier<VirtualFile> createExample() {
		return FelexAddon.get().getDictFolder().then(f -> f.createFile("Example" + DICT_EXT)).map(f -> {
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
}
