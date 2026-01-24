package me.aap.utils.net.http;

import java.nio.ByteBuffer;

import me.aap.utils.log.Log;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author Andrey Pavlenko
 */
public enum HttpMethod {
	GET, HEAD, POST, UNSUPPORTED;
	final byte[] bytes;

	HttpMethod() {
		bytes = name().getBytes(US_ASCII);
	}

	static HttpMethod get(ByteBuffer buf, int start, int end) {
		switch ((char) buf.get(start)) {
			case 'G':
				if ((end - start) < 3) return null;
				return (buf.get(start + 1) == 'E') && (buf.get(start + 2) == 'T') ? GET : UNSUPPORTED;
			case 'H':
				if ((end - start) < 4) return null;
				return (buf.get(start + 1) == 'E') && (buf.get(start + 2) == 'A') && (buf.get(start + 3) == 'D') ? HEAD : UNSUPPORTED;
			case 'P':
				if ((end - start) < 4) return null;
				return (buf.get(start + 1) == 'O') && (buf.get(start + 2) == 'S') && (buf.get(start + 3) == 'T') ? POST : UNSUPPORTED;
			default:
				return UNSUPPORTED;
		}
	}

	int length() {
		return bytes.length;
	}

	public static HttpMethod fromString(String s) {
		switch (s) {
			case "GET":
				return GET;
			case "POST":
				return POST;
			case "HEAD":
				return HEAD;
			default:
				Log.w(HttpMethod.class.getName(), "Unsupported method: " + s);
				return null;
		}
	}
}
