package me.aap.utils.net.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

import me.aap.utils.log.Log;
import me.aap.utils.net.NetChannel;
import me.aap.utils.net.http.HttpError.BadRequest;
import me.aap.utils.net.http.HttpError.MethodNotAllowed;
import me.aap.utils.net.http.HttpError.NotFound;
import me.aap.utils.net.http.HttpError.PayloadTooLarge;
import me.aap.utils.net.http.HttpError.UriTooLong;
import me.aap.utils.net.http.HttpError.VersionNotSupported;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author Andrey Pavlenko
 */
public abstract class HttpRequestEncoder extends HttpMessageEncoder<HttpRequest> {
	private static final byte[] H_RANGE = "aAnNgGeE".getBytes(US_ASCII);

	@Nullable
	protected abstract HttpRequestHandler getHandler(CharSequence path, HttpMethod method, HttpVersion version);

	@Override
	boolean encodeMessage(NetChannel channel, ByteBuffer buf, Throwable fail) {
		if (fail != null) {
			if (channel.isOpen()) {
				channel.close();
				Log.d(fail, "Failed to read HTTP request");
			}
			return false;
		}

		int start = buf.position();
		int end = buf.limit();

		if (start == end) { // End of stream
			Log.d("HTTP Stream closed");
			channel.close();
			return false;
		}

		HttpMethod method = HttpMethod.get(buf, start, end);

		if (method == null) {
			incompleteMessage(channel, buf, start, end);
			return false;
		} else if (method == HttpMethod.UNSUPPORTED) {
			onError(channel, MethodNotAllowed.instance);
			return false;
		}

		int off = start + method.length();

		if (off == end) {
			incompleteMessage(channel, buf, start, end);
			return false;
		}

		if (buf.get(off) != ' ') {
			onError(channel, BadRequest.instance);
			return false;
		}

		int hash = 0;
		int uriStart = ++off;
		int pathEnd = 0;
		int uriEnd = 0;

		for (; off < end; off++) {
			char c = (char) buf.get(off);

			if (c == '?') {
				pathEnd = off++;
				break;
			} else if (c == ' ') {
				uriEnd = pathEnd = off++;
				break;
			} else {
				hash = 31 * hash + c;
			}
		}

		if (uriEnd == 0) {
			for (; off < end; off++) {
				if (buf.get(off) == ' ') {
					uriEnd = off++;
					break;
				}
			}
		}

		if (uriEnd == 0) {
			incompleteMessage(channel, buf, start, end);
			return false;
		}

		HttpVersion version = HttpVersion.get(buf, off, end);

		if (version == null) {
			incompleteMessage(channel, buf, start, end);
			return false;
		} else if (version == HttpVersion.UNSUPPORTED) {
			onError(channel, VersionNotSupported.instance);
			return false;
		}

		for (off++; off < end; off++) {
			if (buf.get(off) == '\n') {
				if ((++off == getMaxLen()) && (start == 0)) {
					onError(channel, UriTooLong.instance);
					return false;
				}

				Req req = new Req(channel, version, method, buf, uriStart, uriEnd - uriStart, pathEnd - uriStart, hash, off);
				HttpRequestHandler handler = getHandler(req, method, version);

				if (handler == null) {
					onError(channel, NotFound.instance);
					return false;
				}

				if (off < end) {
					return encodeHeaders(channel, buf, null, handler, req, off, false);
				} else {
					int o = off - start;
					req.uriStart -= start;
					req.headerStart -= start;
					channel.read(retainBuf(buf, start, end),
							(b, f) -> encodeHeaders(channel, b, f, handler, req, o, true));
					return false;
				}
			}
		}

		incompleteMessage(channel, buf, start, end);
		return false;
	}

	private boolean encodeHeaders(NetChannel channel, ByteBuffer buf, Throwable fail,
																HttpRequestHandler handler, Req req, int off, boolean readNext) {
		if (fail != null) {
			if (channel.isOpen()) {
				channel.close();
				Log.d(fail, "Failed to read HTTP request");
			}
			return false;
		}

		int start = buf.position();
		int end = buf.limit();

		if (start == end) { // End of stream
			Log.d("HTTP Stream closed");
			channel.close();
			return false;
		}

		loop:
		for (int i = off; i < end; ) {
			switch (buf.get(i)) {
				case 'C':
				case 'c':
					int value = encodeHeaderC(req, buf, i, end);

					if (value < 0) {
						if (value == Integer.MIN_VALUE) break loop;
						i = -value;
					} else {
						i = value;
					}

					break;
				case 'R':
				case 'r':
					value = headerMatch(H_RANGE, buf, i + 1, end);

					if (value < 0) {
						if (value == Integer.MIN_VALUE) break loop;
						i = -value;
						req.rangeStart = (i - req.headerStart);
						break;
					}

					i = value;
					break;
				case 'T':
				case 't':
					value = encodeHeaderT(req, buf, i, end);

					if (value < 0) {
						if (value == Integer.MIN_VALUE) break loop;
						i = -value;
					} else {
						i = value;
					}

					break;
			}

			for (; i < end; i++) {
				if (buf.get(i) != '\n') continue;

				if (i < (end - 1)) {
					if (buf.get(i + 1) != '\n') {
						if (buf.get(i + 1) == '\r') {
							if (i < (end - 2)) {
								if (buf.get(i += 2) != '\n') {
									off = i;
									continue loop;
								}
							} else {
								off = i;
								break loop;
							}
						} else {
							off = ++i;
							continue loop;
						}
					} else {
						i++;
					}
				} else {
					off = i;
					break loop;
				}

				buf.position(i + 1);
				return handleResult(channel, buf, req, handler::handleRequest, readNext);
			}
		}

		if ((start == 0) && (off == getMaxLen())) {
			onError(channel, PayloadTooLarge.instance);
		} else {
			req.uriStart -= start;
			req.headerStart -= start;
			int o = off - start;
			channel.read(retainBuf(buf, start, end),
					(b, f) -> encodeHeaders(channel, b, f, handler, req, o, true));
		}

		return false;
	}

	@Override
	protected void onMessageTooLong(NetChannel channel) {
		onError(channel, UriTooLong.instance);
	}

	protected void onError(NetChannel channel, HttpError err) {
		err.write(channel);
	}

	private static final class Req extends HttpMessageBase implements HttpRequest, CharSequence {
		final NetChannel channel;
		final HttpMethod method;
		int uriStart;
		final int uriLen;
		final int pathLen;
		final int hash;
		int rangeStart = -1;

		public Req(NetChannel channel, HttpVersion version, HttpMethod method, ByteBuffer buf, int uriStart, int uriLen,
							 int pathLen, int hash, int headerStart) {
			super(version, buf, headerStart);
			this.channel = channel;
			this.uriStart = uriStart;
			this.uriLen = uriLen;
			this.pathLen = pathLen;
			this.hash = hash;
			this.method = method;
		}

		@NonNull
		@Override
		public NetChannel getChannel() {
			return channel;
		}

		@Nullable
		@Override
		public Range getRange() {
			checkReleased();
			return (rangeStart == -1) ? null : Range.parse(buf, headerStart + rangeStart, headerEnd);
		}

		@Override
		public boolean equals(Object o) {
			checkReleased();
			if (!(o instanceof CharSequence)) return false;

			CharSequence s = (CharSequence) o;
			int len = length();
			if (len != s.length()) return false;

			for (int i = 0, b = uriStart; i < len; i++, b++) {
				if (s.charAt(i) != ((char) buf.get(b))) return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public int length() {
			return pathLen;
		}

		@Override
		public char charAt(int index) {
			checkReleased();
			return (char) buf.get(uriStart + index);
		}

		@NonNull
		@Override
		public CharSequence subSequence(int start, int end) {
			checkReleased();
			return new AsciiSeq(buf, uriStart + start, end - start);
		}

		@NonNull
		@Override
		public HttpMethod getMethod() {
			return method;
		}

		@NonNull
		@Override
		public CharSequence getUri() {
			checkReleased();
			if (uriLen == pathLen) return this;
			return new AsciiSeq(buf, uriStart, uriLen);
		}

		@NonNull
		@Override
		public CharSequence getPath() {
			checkReleased();
			return this;
		}

		@Nullable
		@Override
		public CharSequence getQuery() {
			checkReleased();
			if (uriLen == pathLen) return null;
			return new AsciiSeq(buf, uriStart + pathLen + 1, uriLen - pathLen - 1);
		}

		@Override
		public String toString() {
			int start = uriStart - method.length() - 1;
			byte[] bytes = new byte[headerEnd - start];
			((ByteBuffer) buf.duplicate().position(start)).get(bytes);
			return new String(bytes, US_ASCII);
		}
	}
}
