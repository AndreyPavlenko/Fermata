package me.aap.fermata.storage;

import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.util.Utils;

/**
 * @author Andrey Pavlenko
 */
class MediaStoreFile implements MediaFile {
	private final MediaStoreDir parent;
	private final String name;
	private final String id;

	MediaStoreFile(MediaStoreDir parent, String name, String id) {
		this.parent = parent;
		this.name = name;
		this.id = id;
	}

	@NonNull
	@Override
	public Uri getUri() {
		return DocumentsContract.buildDocumentUriUsingTree(getRootUri(), getId());
	}

	@Nullable
	@Override
	public Uri getAudioUri() {
		return Utils.getAudioUri(getUri());
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@Nullable
	@Override
	public MediaStoreDir getParent() {
		return parent;
	}

	@NonNull
	@Override
	public String toString() {
		return getName();
	}

	@Override
	public int hashCode() {
		return getId().hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof MediaStoreFile) {
			return getId().equals(((MediaStoreFile) obj).getId());
		} else {
			return false;
		}
	}

	@NonNull
	String getId() {
		return id;
	}

	@NonNull
	Uri getRootUri() {
		return getRoot().getUri();
	}

	@NonNull
	MediaStoreFile getRoot() {
		return getParent().getRoot();
	}
}
