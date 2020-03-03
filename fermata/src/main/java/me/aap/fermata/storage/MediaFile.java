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

	default boolean isDirectory() {
		return false;
	}

	default List<MediaFile> ls() {
		return Collections.emptyList();
	}
}
