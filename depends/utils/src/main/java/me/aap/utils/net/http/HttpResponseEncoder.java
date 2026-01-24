package me.aap.utils.net.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

import me.aap.utils.net.NetChannel;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author Andrey Pavlenko
 */
public abstract class HttpResponseEncoder extends HttpMessageEncoder<HttpResponse> {
	private static final byte[] H_ETAG = "tTaAgG".getBytes(US_ASCII);
	private static final byte[] H_LOCATION = "oOcCaAtTiIoOnN".getBytes(US_ASCII);

	@NonNull
	protected abstract HttpResponseHandler getHandler();

	@Override
	boolean encodeMessage(NetChannel channel, ByteBuffer buf, Throwable fail) {
		if (fail != null) {
			onFailure(channel, fail);
			return false;
		}

		int start = buf.position();
		int end = buf.limit();

		if (start == end) { // End of stream
			onFailure(channel, new IOException("HTTP Stream closed"));
			return false;
		}

		HttpVersion version = HttpVersion.get(buf, start, end);

		if (version == null) {
			incompleteMessage(channel, buf, start, end);
			return false;
		} else if (version == HttpVersion.UNSUPPORTED) {
			onUnsupportedVersion(channel);
			return false;
		}

		int off = start + version.length();

		if (off == end) {
			incompleteMessage(channel, buf, start, end);
			return false;
		}

		if (buf.get(off) != ' ') {
			onInvalidMessage(channel);
			return false;
		}

		int code = ++off;
		int reasonStart = 0;

		for (; off < end; off++) {
			if (buf.get(off) == ' ') {
				reasonStart = ++off;
				break;
			}
		}

		if ((reasonStart == 0) || (reasonStart >= end)) {
			incompleteMessage(channel, buf, start, end);
			return false;
		}

		code = (int) HttpUtils.parseLong(buf, code, reasonStart, " ", -1);

		if (code == -1) {
			onInvalidMessage(channel);
			return false;
		}

		for (off++; off < end; off++) {
			if (buf.get(off) == '\n') {
				if ((++off == getMaxLen()) && (start == 0)) {
					onMessageTooLong(channel);
					return false;
				}

				HttpResponseHandler handler = getHandler();
				Resp resp = new Resp(getConnection(channel), version, buf, code, reasonStart, off);

				if (off < end) {
					return encodeHeaders(channel, buf, null, handler, resp, off, false);
				} else {
					int o = off - start;
					resp.reasonStart -= start;
					resp.headerStart -= start;
					channel.read(retainBuf(buf, start, end),
							(b, f) -> encodeHeaders(channel, b, f, handler, resp, o, true));
					return false;
				}
			}
		}

		incompleteMessage(channel, buf, start, end);
		return false;
	}

	protected HttpConnection getConnection(NetChannel channel) {
		return new HttpConnection(channel);
	}

	private boolean encodeHeaders(NetChannel channel, ByteBuffer buf, Throwable fail,
																HttpResponseHandler handler, Resp resp, int off, boolean readNext) {
		if (fail != null) {
			onFailure(channel, fail);
			return false;
		}

		int start = buf.position();
		int end = buf.limit();

		if (start == end) { // End of stream
			onFailure(channel, new IOException("HTTP Stream closed"));
			return false;
		}

		loop:
		for (int i = off; i < end; ) {
			switch (buf.get(i)) {
				case 'C':
				case 'c':
					int value = encodeHeaderC(resp, buf, i, end);

					if (value < 0) {
						if (value == Integer.MIN_VALUE) break loop;
						i = -value;
					} else {
						i = value;
					}

					break;
				case 'E':
				case 'e':
					value = headerMatch(H_ETAG, buf, i + 1, end);

					if (value < 0) {
						if (value == Integer.MIN_VALUE) break loop;
						i = -value;
						resp.etagStart = (i - resp.headerStart);
						break;
					}

					i = value;
					break;
				case 'L':
				case 'l':
					value = headerMatch(H_LOCATION, buf, i + 1, end);

					if (value < 0) {
						if (value == Integer.MIN_VALUE) break loop;
						i = -value;
						resp.locationStart = (i - resp.headerStart);
						break;
					}

					i = value;
					break;
				case 'T':
				case 't':
					value = encodeHeaderT(resp, buf, i, end);

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
				return handleResult(channel, buf, resp, handler::handleResponse, readNext);
			}
		}

		if ((start == 0) && (off == getMaxLen())) {
			onMessageTooLong(channel);
		} else {
			resp.reasonStart -= start;
			resp.headerStart -= start;
			int o = off - start;
			channel.read(retainBuf(buf, start, end),
					(b, f) -> encodeHeaders(channel, b, f, handler, resp, o, true));
		}

		return false;
	}

	@Override
	protected void onFailure(NetChannel channel, Throwable fail) {
		getHandler().onFailure(channel, fail);
	}

	@Override
	protected void onMessageTooLong(NetChannel channel) {
		onFailure(channel, new IOException("HTTP response is too long"));
	}

	protected void onInvalidMessage(NetChannel channel) {
		onFailure(channel, new IOException("Invalid HTTP response"));
	}

	protected void onUnsupportedVersion(NetChannel channel) {
		onFailure(channel, new IOException("Unsupported HTTP version"));
	}

	private static final class Resp extends HttpMessageBase implements HttpResponse {
		final HttpConnection connection;
		final int code;
		int reasonStart;
		int locationStart = -1;
		int etagStart = -1;

		Resp(HttpConnection connection, HttpVersion version, ByteBuffer buf, int code, int reasonStart, int headerStart) {
			super(version, buf, headerStart);
			this.connection = connection;
			this.code = code;
			this.reasonStart = reasonStart;
		}

		@NonNull
		@Override
		public HttpConnection getConnection() {
			return connection;
		}

		@Override
		public int getStatusCode() {
			return code;
		}

		@NonNull
		@Override
		public CharSequence getReason() {
			int end = HttpUtils.indexOfChar(buf, reasonStart, headerStart, "\r\n");
			return new AsciiSeq(buf, reasonStart, end - reasonStart);
		}

		@Nullable
		@Override
		public CharSequence getLocation() {
			return getHeaderValue(locationStart);
		}

		@Nullable
		@Override
		public CharSequence getEtag() {
			return getHeaderValue(etagStart);
		}

		@Override
		public String toString() {
			byte[] bytes = new byte[headerEnd - headerStart];
			((ByteBuffer) buf.duplicate().position(headerStart)).get(bytes);
			return getVersion().toString() + ' ' + getStatusCode() + ' ' + getReason() + "\r\n" +
					new String(bytes, US_ASCII);
		}
	}
}
