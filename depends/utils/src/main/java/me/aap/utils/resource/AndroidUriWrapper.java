package me.aap.utils.resource;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
class AndroidUriWrapper implements Rid {
	private final Uri uri;
	private int hash;

	AndroidUriWrapper(Uri uri) {
		this.uri = uri;
	}

	public Uri getUri() {
		return uri;
	}

	@Nullable
	@Override
	public String getScheme() {
		return uri.getScheme();
	}

	@Nullable
	@Override
	public String getAuthority() {
		return uri.getAuthority();
	}

	@Nullable
	@Override
	public String getUserInfo() {
		return uri.getUserInfo();
	}

	@Nullable
	@Override
	public String getHost() {
		return uri.getHost();
	}

	@Override
	public int getPort() {
		return uri.getPort();
	}

	@Nullable
	@Override
	public String getPath() {
		return uri.getPath();
	}

	@Nullable
	@Override
	public String getQuery() {
		return uri.getQuery();
	}

	@Nullable
	@Override
	public String getFragment() {
		return uri.getFragment();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o instanceof AndroidUriWrapper) return (uri.equals(((AndroidUriWrapper) o).uri));
		if (!(o instanceof Rid)) return false;
		return toString().endsWith(toString());
	}

	@Override
	public int hashCode() {
		return (hash == 0) ? (hash = toString().hashCode()) : hash;
	}

	@NonNull
	@Override
	public String toString() {
		return uri.toString();
	}
}
