package me.aap.fermata.storage;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public interface MediaFile {

	@Nullable
	static MediaFile resolve(String uri, MediaFile relativeTo) {
		return uri.contains(":/") ? MediaFile.create(uri) : relativeTo.findChild(uri);
	}

	static MediaFile create(String uri) {
		if (uri.startsWith("file:/")) return FsFile.create(Uri.parse(uri));
		else if (uri.startsWith("content:/")) return create(Uri.parse(uri), false);
		else return new NetFile(Uri.parse(uri));
	}

	static MediaFile create(Uri rootUri, boolean preferFs) {
		if ("file".equals(rootUri.getScheme())) return FsFile.create(rootUri);

		MediaStoreDir msDir = MediaStoreDir.create(rootUri);

		if (preferFs) {
			FsFile f = FsFile.create(msDir);
			if (f != null) return f;
		}

		return msDir;
	}

	@NonNull
	String getName();

	@NonNull
	Uri getUri();

	@Nullable
	default Uri getAudioUri() {
		return null;
	}

	@Nullable
	default String getPath() {
		return null;
	}

	@Nullable
	default MediaFile getParent() {
		return null;
	}

	@Nullable
	default MediaFile getChild(String name) {
		for (MediaFile f : ls()) {
			if (name.equals(f.getName())) return f;
		}
		return null;
	}

	@Nullable
	default MediaFile findChild(String name) {
		String[] names = name.split("/");
		MediaFile f = this;

		for (String n : names) {
			f = f.getChild(n);
			if (f == null) break;
		}

		return (f == this) ? null : f;
	}

	default boolean isDirectory() {
		return false;
	}

	default boolean isLocalFile() {
		return true;
	}

	default List<MediaFile> ls() {
		return Collections.emptyList();
	}
}
