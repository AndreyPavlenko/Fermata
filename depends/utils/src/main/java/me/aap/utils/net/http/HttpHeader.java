package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.US_ASCII;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Andrey Pavlenko
 */
public enum HttpHeader {
	ACCEPT("Accept", "*/*"),
	ACCEPT_ENCODING("Accept-Encoding", "gzip,deflate"),
	ACCEPT_RANGES("Accept-Ranges", "bytes"),
	AUTHORIZATION("Authorization"),
	CONNECTION("Connection", "close"),
	CONTENT_ENCODING("Content-Encoding"),
	CONTENT_LENGTH("Content-Length"),
	CONTENT_RANGE("Content-Range"),
	CONTENT_TYPE("Content-Type"),
	ETAG("ETag"),
	HOST("Host"),
	IF_NONE_MATCH("If-None-Match"),
	LOCATION("Location"),
	TRANSFER_ENCODING("Transfer-Encoding", "chunked"),
	USER_AGENT("User-Agent", getAgent()),
	;
	private final String name;
	private final String value;
	private byte[] nameBytes;
	private byte[] valueBytes;

	HttpHeader(String name) {
		this(name, null);
	}

	HttpHeader(String name, String value) {
		this.name = name;
		this.value = value;
	}

	@NonNull
	public String getName() {
		return name;
	}

	public int getNameLength() {
		return getName().length();
	}

	public void appendName(ByteBuffer buf) {
		if (nameBytes == null) nameBytes = name.getBytes(US_ASCII);
		buf.put(nameBytes);
	}

	@Nullable
	public String getDefaultValue() {
		return value;
	}

	public int getDefaultValueLength() {
		return (value == null) ? 0 : value.length();
	}

	public void appendDefaultValue(ByteBuffer buf) {
		if (value == null)
			throw new IllegalArgumentException("Header " + getName() + " does not have a default value");
		if (valueBytes == null) valueBytes = value.getBytes(US_ASCII);
		buf.put(valueBytes);
	}

	@NonNull
	@Override
	public String toString() {
		return getName();
	}

	private static String getAgent() {
		String a = System.getProperty("http.agent");
		return (a != null) ? a : "Java/" + System.getProperty("java.version");
	}
}
