package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.utils.concurrent.NetThread.isWriteBuffer;
import static me.aap.utils.io.IoUtils.emptyByteBufferArray;
import static me.aap.utils.net.http.HttpHeader.CONTENT_LENGTH;
import static me.aap.utils.net.http.HttpVersion.HTTP_1_1;

import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import me.aap.utils.BuildConfig;
import me.aap.utils.concurrent.NetThread;
import me.aap.utils.function.CheckedConsumer;
import me.aap.utils.function.Function;
import me.aap.utils.io.ByteBufferOutputStream;
import me.aap.utils.io.IoUtils;
import me.aap.utils.net.ByteBufferArraySupplier;

/**
 * @author Andrey Pavlenko
 */
class HttpMessageBuilder implements HttpRequestBuilder, HttpResponseBuilder {
	private static final byte[] EOL = new byte[]{(byte) '\r', (byte) '\n'};
	private static final byte[] SEP = new byte[]{(byte) ':', (byte) ' '};
	private static final byte[] OK = " 200 OK\r\n".getBytes(US_ASCII);
	private static final byte[] PART = " 206 Partial content\r\n".getBytes(US_ASCII);
	private static final byte[] INT_PLACEHOLDER = "          ".getBytes(US_ASCII);
	private static final int MAX_DEFAULT_CAPACITY = 4096;
	private static int defaultCapacity = 256;
	private ByteBuffer buf;
	private int pos;
	private boolean responseBuf;

	HttpMessageBuilder() {
		this(defaultCapacity);
	}

	HttpMessageBuilder(int initCapacity) {
		this(ByteBuffer.allocate(initCapacity));
	}

	HttpMessageBuilder(boolean useResponseBuf) {
		if (useResponseBuf) {
			buf = NetThread.getWriteBuffer();
			responseBuf = isWriteBuffer(buf);
			return;
		}

		buf = ByteBuffer.allocate(defaultCapacity);
		pos = 0;
		responseBuf = false;
	}

	HttpMessageBuilder(ByteBuffer buf) {
		this.buf = buf;
		pos = buf.position();
	}

	static ByteBufferArraySupplier supplier(Function<? super HttpMessageBuilder, ByteBuffer[]> builder) {
		return new ByteBufferArraySupplier() {
			ByteBuffer[] array;
			boolean responseBuf;

			@Override
			public ByteBuffer[] getByteBufferArray() {
				if (array == null) {
					HttpMessageBuilder b = new HttpMessageBuilder(true);
					array = builder.apply(b);
					responseBuf = b.responseBuf;
					if (BuildConfig.D && responseBuf)
						NetThread.assertWriteBuffer(array[0]);
				}

				return array;
			}

			@Override
			public ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
				assert array != null;
				assert bb == array;

				if (responseBuf) {
					if (fromIndex == 0) {
						if (BuildConfig.D) NetThread.assertWriteBuffer(array[0]);
						array[0] = IoUtils.copyOf(array[0]);
					}

					responseBuf = false;
				}

				if (fromIndex != 0) array = Arrays.copyOfRange(array, fromIndex, bb.length);
				return this;
			}

			@Override
			public void release() {
				array = emptyByteBufferArray();
			}
		};
	}

	@Override
	public HttpMessageBuilder setRequest(CharSequence uri) {
		return setRequest(uri, HttpMethod.GET);
	}

	@Override
	public HttpMessageBuilder setRequest(CharSequence uri, HttpMethod m) {
		return setRequest(uri, m, HTTP_1_1);
	}

	@Override
	public HttpMessageBuilder setRequest(CharSequence uri, HttpMethod m, HttpVersion version) {
		int uriLen = uri.length();
		ensureCapacity(uriLen + m.length() + version.length() + 4);
		buf.put(m.bytes).put((byte) ' ');
		append(uri, uriLen);
		buf.put((byte) ' ').put(version.bytes).put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder setStatusOk(HttpVersion version) {
		ensureCapacity(version.bytes.length + OK.length);
		buf.put(version.bytes).put(OK);
		return this;
	}

	@Override
	public HttpMessageBuilder setStatusPartial(HttpVersion version) {
		ensureCapacity(version.bytes.length + PART.length);
		buf.put(version.bytes).put(PART);
		return this;
	}

	@Override
	public HttpMessageBuilder setStatus(HttpVersion version, CharSequence status) {
		int statusLen = status.length();
		ensureCapacity(statusLen + version.length() + 3);
		buf.put(version.bytes).put((byte) ' ');
		append(status, statusLen);
		buf.put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder addHeader(HttpHeader h) {
		int nameLen = h.getNameLength();
		int valueLen = h.getDefaultValueLength();
		ensureCapacity(nameLen + valueLen + 4);
		h.appendName(buf);
		buf.put(SEP);
		h.appendDefaultValue(buf);
		buf.put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder addHeader(HttpHeader h, long value) {
		long v = (value < 0) ? -value : value;
		int nameLen = h.getNameLength();
		int valueLen = numberOfDigits(v);
		ensureCapacity(nameLen + valueLen + 5);
		h.appendName(buf);
		buf.put(SEP);
		if (value < 0) buf.put((byte) '-');
		buf.position(buf.position() + valueLen);
		appendNumber(buf.position() - 1, v);
		buf.put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder addHeader(CharSequence name, long value) {
		long v = (value < 0) ? -value : value;
		int nameLen = name.length();
		int valueLen = numberOfDigits(v);
		ensureCapacity(nameLen + valueLen + 5);
		append(name, nameLen);
		buf.put(SEP);
		if (value < 0) buf.put((byte) '-');
		buf.position(buf.position() + valueLen);
		appendNumber(buf.position() - 1, v);
		buf.put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder addHeader(HttpHeader h, CharSequence value) {
		int nameLen = h.getNameLength();
		int valueLen = value.length();
		ensureCapacity(nameLen + valueLen + 4);
		h.appendName(buf);
		buf.put(SEP);
		append(value, valueLen);
		buf.put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder addHeader(CharSequence name, CharSequence value) {
		int nameLen = name.length();
		int valueLen = value.length();
		ensureCapacity(nameLen + valueLen + 4);
		append(name, nameLen);
		buf.put(SEP);
		append(value, valueLen);
		buf.put(EOL);
		return this;
	}

	@Override
	public HttpMessageBuilder addHeader(CharSequence line) {
		int len = line.length();
		ensureCapacity(len + 2);
		append(line, len);
		buf.put(EOL);
		return this;
	}

	@Override
	public ByteBuffer[] build() {
		ensureCapacity(2);
		buf.put(EOL);
		buf.limit(buf.position()).position(pos);
		return new ByteBuffer[]{buf};
	}

	@Override
	public ByteBuffer[] build(ByteBuffer payload) {
		addHeader(CONTENT_LENGTH, payload.remaining());
		ensureCapacity(2);
		buf.put(EOL);
		buf.limit(buf.position()).position(pos);
		return new ByteBuffer[]{buf, payload};
	}

	@Override
	public <E extends Throwable> ByteBuffer[] build(CheckedConsumer<OutputStream, E> payloadWriter) throws E {
		ensureCapacity(CONTENT_LENGTH.getNameLength() + INT_PLACEHOLDER.length + 6);
		CONTENT_LENGTH.appendName(buf);
		buf.put(SEP);
		buf.put(INT_PLACEHOLDER);
		int numPos = buf.position() - 1;
		buf.put(EOL);
		buf.put(EOL);
		ByteBufferOutputStream s = new ByteBufferOutputStream(buf);
		payloadWriter.accept(s);

		ByteBuffer bb = s.getBuffer();

		if (bb != buf) {
			buf = bb;
			responseBuf = false;
		}

		int len = buf.position() - numPos - 5;
		appendNumber(numPos, len);
		buf.limit(buf.position()).position(pos);
		return new ByteBuffer[]{buf};
	}

	private void append(CharSequence str, int len) {
		ByteBuffer buf = this.buf;

		if (buf.hasArray()) {
			byte[] b = buf.array();
			for (int i = 0, off = buf.arrayOffset() + buf.position(); i < len; i++) {
				b[off + i] = (byte) str.charAt(i);
			}
			buf.position(buf.position() + len);
		} else {
			for (int i = 0; i < len; i++) {
				buf.put((byte) str.charAt(i));
			}
		}
	}

	private void appendNumber(int pos, long value) {
		do {
			buf.put(pos--, (byte) ('0' + (value % 10)));
			value /= 10;
		} while (value != 0);
	}

	private void ensureCapacity(int len) {
		if (buf.remaining() < len) {
			int capacity = buf.capacity();
			int newCapacity = capacity << 1;
			if (newCapacity < 0) throw new BufferOverflowException();
			if ((newCapacity > defaultCapacity) && (newCapacity <= MAX_DEFAULT_CAPACITY))
				defaultCapacity = newCapacity;

			ByteBuffer b = ByteBuffer.allocate(newCapacity);
			buf.limit(buf.position()).position(pos);
			b.put(buf);
			buf = b;
			pos = 0;
			responseBuf = false;
		}
	}

	private static int numberOfDigits(long positiveNum) {
		long p = 10;
		for (int i = 1; i < 19; i++) {
			if (positiveNum < p) return i;
			p = 10 * p;
		}
		return 19;
	}
}
