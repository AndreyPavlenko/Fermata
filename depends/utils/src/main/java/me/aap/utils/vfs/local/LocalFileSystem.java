package me.aap.utils.vfs.local;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.utils.app.App;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CacheMap;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.io.RandomAccessFileChannelWrapper;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

public class LocalFileSystem implements VirtualFileSystem {
	private static final LocalFileSystem instance =
			new LocalFileSystem(LocalFileSystem::androidRoots);
	private final Supplier<Collection<File>> roots;
	private final CacheMap<File, CachedFileChannel> fileCache = new CacheMap<>(60);

	LocalFileSystem(Supplier<Collection<File>> roots) {
		this.roots = roots;
	}

	public static LocalFileSystem getInstance() {
		return instance;
	}

	@NonNull
	@Override
	public Provider getProvider() {
		return Provider.getInstance();
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		return completed(getResource(getPath(rid)));
	}

	public VirtualResource getResource(String path) {
		if (path == null) return null;
		File file = new File(path);
		if (file.isDirectory()) return new LocalFolder(file);
		if (file.isFile()) return new LocalFile(file);
		return null;
	}

	@Override
	public FutureSupplier<VirtualFile> getFile(Rid rid) {
		return completed(getFile(new File(getPath(rid))));
	}

	public VirtualFile getFile(File f) {
		return new LocalFile(f);
	}

	@Override
	public FutureSupplier<VirtualFolder> getFolder(Rid rid) {
		return completed(getFolder(new File(getPath(rid))));
	}

	public VirtualFolder getFolder(File f) {
		return new LocalFolder(f);
	}

	@NonNull
	@Override
	public FutureSupplier<List<VirtualFolder>> getRoots() {
		Collection<File> roots = this.roots.get();
		int size = roots.size();
		if (size == 0) return completedEmptyList();

		List<VirtualFolder> folders = new ArrayList<>(size);
		for (File f : roots) {
			folders.add(new LocalFolder(f, null));
		}
		return completed(folders);
	}

	@SuppressWarnings("JavaReflectionMemberAccess")
	@SuppressLint({"DiscouragedPrivateApi", "SdCardPath"})
	public static Collection<File> androidRoots() {
		Context ctx = App.get();
		if (ctx == null) return Collections.emptyList();

		Set<File> files = new HashSet<>();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
			addRoot(files, ctx.getDataDir());

			if (sm != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					for (StorageVolume v : sm.getStorageVolumes()) addRoot(files, v.getDirectory());
				} else {
					Class<?> c = StorageVolume.class;

					try {
						Method m = c.getDeclaredMethod("getPathFile");
						m.setAccessible(true);
						for (StorageVolume v : sm.getStorageVolumes()) addRoot(files, (File) m.invoke(v));
					} catch (Throwable ex) {
						Log.e(ex, "StorageVolume.getPathFile() failed");
					}
				}
			}
		}

		File dir = new File("/");
		if (dir.canRead()) files.add(dir);
		if ((dir = new File("/mnt")).canRead()) files.add(dir);
		if ((dir = new File("/sdcard")).canRead()) files.add(dir);
		if ((dir = new File("/storage")).canRead()) files.add(dir);

		addRoot(files, ctx.getFilesDir());
		addRoot(files, ctx.getCacheDir());
		addRoot(files, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
		addRoot(files, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
		addRoot(files, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));

		addRoot(files, ctx.getObbDirs());
		addRoot(files, ctx.getExternalCacheDirs());
		addRoot(files, ctx.getExternalFilesDirs(null));
		addRoot(files, ctx.getExternalMediaDirs());

		return files;
	}

	private static String getPath(Rid rid) {
		String p = rid.toString();
		return p.startsWith("file:/") ? p.substring(6) : rid.getPath();
	}

	private static void addRoot(Set<File> files, File... dirs) {
		if (dirs == null) return;
		for (File dir : dirs) addRoot(files, dir);
	}

	@SuppressWarnings("StatementWithEmptyBody")
	private static void addRoot(Set<File> files, File dir) {
		if (dir == null) return;
		for (File p = dir.getParentFile(); (p != null) && (p.isDirectory()) && p.canRead();
				 dir = p, p = dir.getParentFile()) {
		}
		if ((dir.isDirectory()) && dir.canRead()) files.add(dir);
	}

	FutureSupplier<Long> getLength(File file) {
		return Completed.completed(file.length());
	}

	@Nullable
	RandomAccessChannel getChannel(File file, String mode) {
		if (mode.indexOf('w') >= 0) {
			try {
				RandomAccessFile raf = new RandomAccessFile(file, "rw");
				FileChannel wc = raf.getChannel();
				FileChannel rc = (mode.indexOf('r') >= 0) ? wc : null;
				return new RandomAccessFileChannelWrapper(rc, wc, raf);
			} catch (FileNotFoundException ex) {
				Log.e(ex, "Failed to open file: ", file);
				return null;
			}
		}

		return fileCache.computeIfAbsent(file, k -> {
			try {
				return new CachedFileChannel(file, new RandomAccessFile(k, mode));
			} catch (Throwable ex) {
				Log.e(ex, "Failed to open file: ", file);
				return null;
			}
		});
	}

	void closeCachedChannels(File... files) {
		for (File file : files) {
			fileCache.compute(file, (k, v) -> {
				IoUtils.close(v);
				return null;
			});
		}
	}

	public static final class Provider implements VirtualFileSystem.Provider {
		private final Set<String> schemes = Collections.singleton("file");
		private static final Provider instance = new Provider();

		private Provider() {
		}

		public static Provider getInstance() {
			return instance;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return schemes;
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(LocalFileSystem.getInstance());
		}
	}

	private static final class CachedFileChannel extends RandomAccessFileChannelWrapper {
		private final File file;

		CachedFileChannel(File file, RandomAccessFile raf) {
			super(raf.getChannel(), raf);
			this.file = file;
		}

		@NonNull
		@Override
		public String toString() {
			return "CachedFileChannel: " + file;
		}

		@Override
		public void close() {
		}

		@Override
		public void close(boolean force) {
			if (!force) return;
			getInstance().fileCache.remove(file, this);
			doClose();
		}

		@Override
		protected void doClose() {
			super.doClose();
		}
	}
}