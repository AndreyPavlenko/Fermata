package me.aap.fermata.storage;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * @author Andrey Pavlenko
 */
class NetFile implements MediaFile {
	private final Uri uri;

	public NetFile(Uri uri) {
		this.uri = uri;
	}

	@NonNull
	@Override
	public String getName() {
		return getUri().toString();
	}

	@NonNull
	@Override
	public Uri getUri() {
		return uri;
	}

	@Override
	public boolean isLocalFile() {
		return false;
	}
}
