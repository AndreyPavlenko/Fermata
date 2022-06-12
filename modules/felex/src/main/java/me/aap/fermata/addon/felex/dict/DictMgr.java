package me.aap.fermata.addon.felex.dict;

import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.misc.Assert.assertMainThread;

import java.io.Closeable;
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
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class DictMgr implements Closeable {
	private static DictMgr instance;
	private final PromiseQueue queue = new PromiseQueue();
	private FutureSupplier<List<Dict>> dictionaries;
	private int counter;

	private DictMgr() {
	}

	public static DictMgr create() {
		assertMainThread();
		if (instance == null) instance = new DictMgr();
		instance.counter++;
		return instance;
	}

	public FutureSupplier<List<Dict>> getDictionaries() {
		checkClosed();
		assertMainThread();
		if (dictionaries == null) {
			dictionaries = load()
					.then(d -> {
						if (!d.isEmpty()) return completed(d);
						return createExample().then(f -> Dict.create(this, (VirtualFile) f))
								.map(Collections::singletonList).ifFail(err -> {
									Log.e(err, "Failed to create example");
									return emptyList();
								});
					})
					.main().onCompletion((d, err) -> {
						if (err != null) {
							Log.e(err, "Failed to load dictionaries");
							dictionaries = null;
						} else {
							dictionaries = completed(d);
						}
					});
		}
		return dictionaries.fork();
	}

	@Override
	public void close() {
		assertMainThread();
		if ((instance != this) || (--instance.counter > 0)) return;
		if (dictionaries != null) {
			dictionaries.then(dicts -> Async.forEach(Dict::close, dicts))
					.main().onCompletion((r, err) -> {
						instance = null;
						dictionaries = null;
						if (err != null) Log.e(err, "Failed to close dictionaries manages");
					});
		} else {
			instance = null;
		}
	}

	private boolean isClosed() {
		return instance != this;
	}

	<T> FutureSupplier<T> enqueue(CheckedSupplier<T, Throwable> task) {
		checkClosed();
		return queue.enqueue(task);
	}

	private FutureSupplier<List<Dict>> load() {
		return FelexAddon.get().getDictFolder().then(folder ->
				enqueue(folder::getChildren).map(FutureSupplier::peek).then(files -> {
					ArrayList<Dict> list = new ArrayList<>(files.size());
					return Async.forEach(f -> (f instanceof VirtualFile) && (f.getName().endsWith(".dict"))
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
		return FelexAddon.get().getDictFolder().then(f -> f.createFile("Example.dict")).map(f -> {
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
			return App.get().getAssets().open("Example_" + lang + ".dict");
		} catch (IOException ex) {
			return App.get().getAssets().open("Example.dict");
		}
	}

	private void checkClosed() {
		if (isClosed()) throw new IllegalStateException();
	}
}
