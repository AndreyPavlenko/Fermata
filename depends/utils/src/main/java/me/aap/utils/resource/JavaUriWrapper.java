package me.aap.utils.resource;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.util.Objects;

/**
 * @author Andrey Pavlenko
 */
class JavaUriWrapper implements Rid {
	private final URI uri;

	JavaUriWrapper(URI uri) {
		this.uri = uri;
	}

	public URI getUri() {
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
		if (o instanceof JavaUriWrapper) return (uri.equals(((JavaUriWrapper) o).uri));
		if (!(o instanceof Rid)) return false;
		return toString().endsWith(toString());
	}

	@Override
	public int hashCode() {
		return Objects.hash(uri);
	}

	@NonNull
	@Override
	public String toString() {
		return uri.toString();
	}
}
