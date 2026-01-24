package me.aap.utils.resource;

import android.net.Uri;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import static me.aap.utils.os.OsUtils.isAndroid;

/**
 * Resource identifier (URI).
 *
 * @author Andrey Pavlenko
 */
public interface Rid {

	String getScheme();

	String getAuthority();

	String getUserInfo();

	String getHost();

	int getPort();

	String getPath();

	String getQuery();

	String getFragment();

	static Rid create(CharSequence rid) {
		if (isAndroid()) {
			return new AndroidUriWrapper(Uri.parse(rid.toString()));
		} else {
			try {
				return new JavaUriWrapper(new URI(rid.toString()));
			} catch (URISyntaxException ex) {
				throw new IllegalArgumentException(ex);
			}
		}
	}

	static Rid create(CharSequence scheme, CharSequence userInfo, CharSequence host, int port,
										CharSequence path) {
		return create(scheme, userInfo, host, port, path, null, null);
	}

	static Rid create(CharSequence scheme, CharSequence userInfo, CharSequence host, int port,
										CharSequence path, CharSequence query, CharSequence fragment) {
		return GenericRid.create(scheme, userInfo, host, port, path, query, fragment);
	}

	static CharSequence encode(CharSequence rid) {
		if (isAndroid()) {
			return Uri.encode(rid.toString());
		} else {
			try {
				return URLEncoder.encode(rid.toString(), "UTF-8");
			} catch (UnsupportedEncodingException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	static CharSequence decode(CharSequence rid) {
		if (isAndroid()) {
			return Uri.decode(rid.toString());
		} else {
			try {
				return URLDecoder.decode(rid.toString(), "UTF-8");
			} catch (UnsupportedEncodingException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	static Rid create(Uri uri) {
		return new AndroidUriWrapper(uri);
	}

	static Rid create(URI uri) {
		return new JavaUriWrapper(uri);
	}

	static Rid create(File file) {
		if (isAndroid()) {
			return new AndroidUriWrapper(Uri.fromFile(file));
		} else {
			return new JavaUriWrapper(file.toURI());
		}
	}

	default Uri toAndroidUri() {
		if (this instanceof AndroidUriWrapper) return ((AndroidUriWrapper) this).getUri();
		return Uri.parse(toString());
	}

	default URI toJavaUri() {
		if (this instanceof JavaUriWrapper) return ((JavaUriWrapper) this).getUri();

		try {
			return new URI(toString());
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException(ex);
		}
	}
}
