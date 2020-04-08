package me.aap.fermata.storage;

import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.util.Utils;
import me.aap.utils.io.FileUtils;
import me.aap.utils.text.SharedTextBuilder;

import static android.provider.DocumentsContract.buildDocumentUriUsingTree;

/**
 * @author Andrey Pavlenko
 */
class FsFile implements MediaFile {
	private final FsFile parent;
	private final String path;
	private final String name;
	private byte isDir;

	private FsFile(FsFile parent, String path, String name) {
		this.parent = parent;
		this.path = path;
		this.name = name;
	}

	static FsFile create(Uri fileUri) {
		return new FileRoot(fileUri, fileUri.getPath());
	}

	static FsFile create(MediaStoreDir msDir) {
		SharedPreferences map = FermataApplication.get().getUriToPathMap();
		String uriStr = msDir.getRootUri().toString();
		String path = map.getString(uriStr, null);
		if (path != null) return new MediaRoot(msDir, path);

		MediaStoreFile msFile = msDir.findAnyFile();
		if (msFile == null) return null;

		File f = FileUtils.getFileFromUri(msFile.getUri());
		if (f == null) return null;

		f = f.getParentFile();

		for (MediaFile p = msFile.getParent(); (p != null) && (f != null); p = p.getParent(), f = f.getParentFile()) {
			if (p == msDir) {
				try {
					path = f.getCanonicalPath();
				} catch (IOException e) {
					path = f.getAbsolutePath();
				}

				map.edit().putString(uriStr, path).apply();
				return new MediaRoot(msDir, path);
			}
		}

		return null;
	}

	@NonNull
	@Override
	public Uri getUri() {
		return Uri.fromFile(getFile());
	}

	@Nullable
	@Override
	public Uri getAudioUri() {
		List<FsFile> parents = new ArrayList<>();
		FsFile root = getRoot(parents);
		if (!(root instanceof MediaRoot)) return null;

		MediaRoot mediaRoot = (MediaRoot) root;
		String rootId = mediaRoot.msDir.getId();

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			if (!rootId.endsWith(":")) tb.append('/');

			for (int i = parents.size() - 2; i >= 0; i--) {
				FsFile p = parents.get(i);
				tb.append(p.getName()).append('/');
			}

			tb.append(getName());
			return Utils.getAudioUri(buildDocumentUriUsingTree(mediaRoot.msDir.getRootUri(), tb.toString()));
		}
	}

	@NonNull
	@Override
	public String getPath() {
		return path;
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	public FsFile getParent() {
		return parent;
	}

	@Nullable
	@Override
	public MediaFile getChild(String name) {
		FsFile f = createChild(name);
		return (f.getFile().exists()) ? f : null;
	}

	@Override
	public List<MediaFile> ls() {
		String[] names = getFile().list();
		if ((names == null) || (names.length == 0)) return Collections.emptyList();

		List<MediaFile> ls = new ArrayList<>(names.length);
		for (String name : names) {
			ls.add(createChild(name));
		}

		return ls;
	}

	@Override
	public boolean isDirectory() {
		if (isDir == 0) isDir = getFile().isDirectory() ? (byte) 1 : (byte) -1;
		return isDir == 1;
	}

	@NonNull
	@Override
	public String toString() {
		return getName();
	}

	@Override
	public int hashCode() {
		return getPath().hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof FsFile) {
			return getPath().equals(((FsFile) obj).getPath());
		} else {
			return false;
		}
	}

	@NonNull
	FsFile getRoot(List<FsFile> parents) {
		FsFile p = getParent();
		assert p != null;
		parents.add(p);
		return p.getRoot(parents);
	}

	private File getFile() {
		return new File(getPath());
	}

	private FsFile createChild(String name) {
		String path = getPath();
		int len = path.length();
		path = path + '/' + name;
		return new FsFile(this, path, path.substring(len + 1));
	}


	private static final class FileRoot extends FsFile {
		private final Uri uri;

		public FileRoot(Uri uri, String path) {
			super(null, path, name(path));
			this.uri = uri;
		}

		@NonNull
		@Override
		FsFile getRoot(List<FsFile> parents) {
			return this;
		}

		@Override
		public FsFile getParent() {
			return null;
		}

		@NonNull
		@Override
		public Uri getUri() {
			return uri;
		}
	}

	private static final class MediaRoot extends FsFile {
		final MediaStoreDir msDir;

		public MediaRoot(MediaStoreDir msDir, String path) {
			super(null, path, name(path));
			this.msDir = msDir;
		}

		@NonNull
		@Override
		FsFile getRoot(List<FsFile> parents) {
			return this;
		}

		@Override
		public FsFile getParent() {
			return null;
		}

		@NonNull
		@Override
		public Uri getUri() {
			return msDir.getUri();
		}
	}

	private static String name(String path) {
		int i = path.lastIndexOf(File.separatorChar);
		return ((i <= 0) || (i == (path.length() - 1))) ? path : path.substring(i + 1);
	}
}
