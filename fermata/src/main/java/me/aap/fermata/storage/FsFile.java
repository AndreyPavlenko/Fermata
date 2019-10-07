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

	static FsFile create(MediaStoreDir msDir) {
		SharedPreferences map = FermataApplication.get().getUriToPathMap();
		String uriStr = msDir.getRootUri().toString();
		String path = map.getString(uriStr, null);
		if (path != null) return new RootFsFile(msDir, path);

		MediaStoreFile msFile = msDir.findAnyFile();
		if (msFile == null) return null;

		File f = Utils.getFileFromUri(msFile.getUri());
		if (f == null) return null;

		f = f.getParentFile();

		for (MediaFile p = msFile.getParent(); f != null; p = p.getParent(), f = f.getParentFile()) {
			if (p == msDir) {
				try {
					path = f.getCanonicalPath();
				} catch (IOException e) {
					path = f.getAbsolutePath();
				}

				map.edit().putString(uriStr, path).apply();
				return new RootFsFile(msDir, path);
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
		RootFsFile root = getRoot(parents);
		String rootId = root.msDir.getId();
		StringBuilder sb = Utils.getSharedStringBuilder().append(rootId);
		if (!rootId.endsWith(":")) sb.append('/');

		for (int i = parents.size() - 2; i >= 0; i--) {
			FsFile p = parents.get(i);
			sb.append(p.getName()).append('/');
		}

		sb.append(getName());
		return Utils.getAudioUri(buildDocumentUriUsingTree(root.msDir.getRootUri(), sb.toString()));
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

	@Override
	public MediaFile getChild(String name) {
		return createChild(name);
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
	RootFsFile getRoot(List<FsFile> parents) {
		FsFile p = getParent();
		parents.add(p);
		return p.getRoot(parents);
	}

	private File getFile() {
		return new File(getPath());
	}

	private MediaFile createChild(String name) {
		String path = getPath();
		int len = path.length();
		path = path + '/' + name;
		return new FsFile(this, path, path.substring(len + 1));
	}


	private static final class RootFsFile extends FsFile {
		final MediaStoreDir msDir;

		public RootFsFile(MediaStoreDir msDir, String path) {
			super(null, path, name(path));
			this.msDir = msDir;
		}

		@NonNull
		@Override
		RootFsFile getRoot(List<FsFile> parents) {
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

		private static String name(String path) {
			int i = path.lastIndexOf(File.separatorChar);
			return ((i <= 0) || (i == (path.length() - 1))) ? path : path.substring(i + 1);
		}
	}
}
