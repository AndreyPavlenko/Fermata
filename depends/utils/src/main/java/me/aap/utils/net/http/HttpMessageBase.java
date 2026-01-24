package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.concurrent.NetThread.getReadBuffer;
import static me.aap.utils.concurrent.NetThread.isReadBuffer;
import static me.aap.utils.io.IoUtils.copyOfRange;
import static me.aap.utils.io.IoUtils.emptyByteBuffer;
import static me.aap.utils.io.IoUtils.ensureCapacity;
import static me.aap.utils.net.http.HttpUtils.parseLong;
import static me.aap.utils.net.http.HttpVersion.HTTP_1_0;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.BuildConfig;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BiFunction;
import me.aap.utils.function.ProgressiveResultConsumer.Completion;
import me.aap.utils.io.AsyncOutputStream;
import me.aap.utils.io.ByteBufferInputStream;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.net.ByteBufferSupplier;
import me.aap.utils.net.http.HttpError.PayloadTooLarge;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
abstract class HttpMessageBase implements HttpMessage {
	final HttpVersion version;
	ByteBuffer buf;
	int headerStart;
	int headerEnd;
	int payloadEnd;
	long contentLen;
	byte connectionClose;
	boolean released;
	int contentTypeStart = -1;
	int contentEncodingStart = -1;
	int transferEncodingStart = -1;


	HttpMessageBase(HttpVersion version, ByteBuffer buf, int headerStart) {
		this.version = version;
		this.buf = buf;
		this.headerStart = headerStart;
	}

	@NonNull
	@Override
	public HttpVersion getVersion() {
		return version;
	}

	@NonNull
	@Override
	public CharSequence getHeaders() {
		checkReleased();
		return new AsciiSeq(buf, headerStart, headerEnd - headerStart);
	}

	@Nullable
	@Override
	public CharSequence getContentType() {
		return getHeaderValue(contentTypeStart);
	}

	@Nullable
	@Override
	public CharSequence getContentEncoding() {
		return getHeaderValue(contentEncodingStart);
	}

	@Nullable
	@Override
	public CharSequence getTransferEncoding() {
		return getHeaderValue(transferEncodingStart);
	}

	@Override
	public long getContentLength() {
		long len = contentLen;
		if (len < 0) {
			contentLen = len = parseLong(buf, (int) (headerStart + -len), headerEnd, "\n\r\t ", 0);
		}
		return len;
	}

	@Override
	public boolean isConnectionClose() {
		return (connectionClose == 1) || ((version == HTTP_1_0) && ((connectionClose != 2)));
	}

	@Override
	public <T> FutureSupplier<T> getPayload(
			BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer, boolean decode, int maxLen) {
		checkReleased();
		long len = getContentLength();

		if (len <= 0) {
			CharSequence te = getTransferEncoding();
			return (te != null) ? getTransferEncodedPayload(te, consumer, decode, maxLen)
					: consumer.apply(emptyByteBuffer(), null);
		}

		int available = payloadEnd - headerEnd;

		if (available == len) {
			ByteBuffer payload = buf.duplicate();
			payload.position(headerEnd).limit(payloadEnd);
			CharSequence enc = decode ? getContentEncoding() : null;
			releaseBuf();
			return (enc != null) ? decode(consumer, payload, enc, MAX_PAYLOAD_LEN)
					: consumer.apply(payload, null);
		}

		if (len > maxLen) {
			if (this instanceof HttpRequest) PayloadTooLarge.instance.write(getChannel());
			else getChannel().close();
			return consumer.apply(null,
					new IOException("Maximum payload length exceeded: len=" + len + ", max=" + maxLen));
		}

		String enc;

		if (decode) {
			CharSequence e = getContentEncoding();
			enc = (e != null) ? e.toString() : null;
		} else {
			enc = null;
		}

		ByteBuffer payload = copyOfRange(buf, headerEnd, payloadEnd, (int) len);
		releaseBuf();
		payload.position(payload.limit()).limit(payload.capacity());
		PayloadPromise<T> p = new PayloadPromise<>(payload, enc, maxLen, consumer);
		getChannel().read(p, p);
		return p;
	}

	private <T> FutureSupplier<T> getTransferEncodedPayload(
			CharSequence te,
			BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer,
			boolean decode, int maxLen) {
		if (!TextUtils.equals("chunked", te)) {
			return consumer.apply(null, new IOException("Unsupported transfer encoding: " + te));
		}

		ChunkedPayloadPromise<T> p;

		if (decode) {
			CharSequence ce = getContentEncoding();
			p = (ce != null) ? new ChunkedEncodedPayloadPromise<>(ce.toString(), maxLen, consumer) :
					new ChunkedPayloadPromise<>(maxLen, consumer);
		} else {
			p = new ChunkedPayloadPromise<>(maxLen, consumer);
		}

		ByteBuffer buf = this.buf;
		releaseBuf();
		p.consume(buf);
		return p;
	}

	@Override
	public FutureSupplier<?> writePayload(AsyncOutputStream out) {
		checkReleased();
		long len = getContentLength();

		if (len <= 0) {
			CharSequence te = getTransferEncoding();

			if (te == null) return completedVoid();

			if (!TextUtils.equals("chunked", te)) {
				return failed(new IOException("Unsupported transfer encoding: " + te));
			}

			WriteChunkedPayloadPromise p = new WriteChunkedPayloadPromise(out);
			ByteBuffer buf = this.buf;
			releaseBuf();
			p.consume(buf);
			return p;
		}

		int available = payloadEnd - headerEnd;
		ByteBuffer payload = buf.duplicate();
		payload.position(headerEnd).limit(payloadEnd);
		releaseBuf();

		if (available > 0) {
			FutureSupplier<Void> w = out.write(out.isAsync() ? IoUtils.copyOf(payload) : payload);
			if (available == len) return w.thenRun(out::endOfStream);
			return w.then(v -> {
				WritePayloadPromise p = new WritePayloadPromise(out, len - available);
				getChannel().read(p, p);
				return p;
			});
		}

		WritePayloadPromise p = new WritePayloadPromise(out, len - available);
		getChannel().read(p, p);
		return p;
	}

	@Override
	public FutureSupplier<?> skipPayload() {
		checkReleased();
		long len = getContentLength();

		if (len <= 0) {
			CharSequence te = getTransferEncoding();

			if (te != null) {
				if (!TextUtils.equals("chunked", te)) {
					return failed(new IOException("Unsupported transfer encoding: " + te));
				}

				SkipChunkedPayloadPromise p = new SkipChunkedPayloadPromise();
				ByteBuffer buf = this.buf;
				releaseBuf();
				p.consume(buf);
				return p;
			}

			return completedVoid();
		}

		releaseBuf();
		int available = payloadEnd - headerEnd;
		if (available == len) return completedVoid();

		SkipPayloadPromise p = new SkipPayloadPromise(len - available);
		getChannel().read(p, p);
		return p;
	}

	void release() {
		released = true;
	}

	void releaseBuf() {
		buf = emptyByteBuffer();
	}

	void checkReleased() {
		if (BuildConfig.D && released) throw new IllegalStateException();
	}

	static <T> FutureSupplier<T> decode(BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer,
																			ByteBuffer payload, CharSequence enc, int max) {
		try {
			return consumer.apply(decode(payload, enc, max), null);
		} catch (Throwable ex) {
			return consumer.apply(null, ex);
		}
	}

	static ByteBuffer decode(ByteBuffer payload, CharSequence enc, int max) throws IOException {
		if (TextUtils.equals("gzip", enc)) {
			try (GZIPInputStream in = new GZIPInputStream(new ByteBufferInputStream(payload))) {
				MemOutputStream out = new MemOutputStream(payload.remaining() * 3, max);
				out.readFrom(in);
				return ByteBuffer.wrap(out.getBuffer(), 0, out.getCount());
			}
		} else if (TextUtils.equals("deflate", enc)) {
			try (InflaterInputStream in = new InflaterInputStream(new ByteBufferInputStream(payload))) {
				MemOutputStream out = new MemOutputStream(payload.remaining() * 3, max);
				out.readFrom(in);
				return ByteBuffer.wrap(out.getBuffer(), 0, out.getCount());
			}
		} else {
			throw new IOException("Unsupported content encoding: " + enc);
		}
	}

	@Nullable
	CharSequence getHeaderValue(int valueStart) {
		checkReleased();
		if (valueStart == -1) return null;
		int start = headerStart + valueStart;
		int end = HttpUtils.indexOfChar(buf, start, headerEnd, "\r\n");
		return new AsciiSeq(buf, start, end - start);
	}

	static final class AsciiSeq implements CharSequence {
		private final ByteBuffer chars;
		private final int off;
		private final int len;

		AsciiSeq(ByteBuffer chars, int off, int len) {
			this.chars = chars;
			this.off = off;
			this.len = len;
		}

		@Override
		public int length() {
			return len;
		}

		@Override
		public char charAt(int index) {
			return (char) chars.get(off + index);
		}

		@NonNull
		@Override
		public CharSequence subSequence(int start, int end) {
			return new AsciiSeq(chars, off + start, end - start);
		}

		@Override
		public String toString() {
			byte[] bytes = new byte[len];
			((ByteBuffer) chars.duplicate().position(off)).get(bytes);
			return new String(bytes, US_ASCII);
		}
	}

	private final class PayloadPromise<T> extends Promise<T> implements Completion<ByteBuffer>, ByteBufferSupplier {
		private final ByteBuffer payload;
		private final String enc;
		private final int maxLen;
		private final BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer;

		public PayloadPromise(ByteBuffer payload, String enc, int maxLen,
													BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer) {
			assert payload.hasRemaining();
			this.payload = payload;
			this.enc = enc;
			this.maxLen = maxLen;
			this.consumer = consumer;
		}

		@Override
		public void onCompletion(ByteBuffer result, Throwable fail) {
			if (fail != null) {
				consumer.apply(null, new IOException("Failed to read payload", fail)).thenComplete(this);
				return;
			}

			if (result.limit() == result.capacity()) {
				result.position(0);
				if (enc != null) decode(consumer, result, enc, maxLen).thenComplete(this);
				else consumer.apply(result, null).thenComplete(this);
			} else {
				result.position(result.limit()).limit(result.capacity());
				getChannel().read(this, this);
			}
		}

		@Override
		public ByteBuffer getByteBuffer() {
			return payload;
		}
	}

	private class ChunkedPayloadPromise<T> extends Promise<T> implements Completion<ByteBuffer>, ByteBufferSupplier {
		final int maxLen;
		private final BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer;
		// Maximum chunk length - 19 + \r\n
		private final ByteBuffer lenBuf = ByteBuffer.allocate(21);
		private ByteBuffer payload;
		private int expectedLen = -1;

		public ChunkedPayloadPromise(int maxLen, BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer) {
			this.maxLen = maxLen;
			this.consumer = consumer;
		}

		void consume(ByteBuffer bb) {
			if (expectedLen > 0) {
				int remain = bb.remaining();

				if (remain >= expectedLen) {
					if (!consume(bb, expectedLen)) return;
				} else {
					if (!consume(bb, remain)) return;
					expectedLen -= remain;
					readNext(bb, expectedLen);
					return;
				}
			}

			while (bb.hasRemaining()) {
				int idx = HttpUtils.indexOfChar(bb, bb.position(), bb.limit(), '\n');

				if (idx == -1) {
					readNext(bb, 0);
					return;
				}

				if ((idx - bb.position()) == 1) { // End of block
					bb.position(idx + 1);
					continue;
				}

				long len = HttpUtils.parseHexLong(bb, bb.position(), idx);

				if (len == 0) {
					idx = HttpUtils.indexOfChar(bb, idx + 1, bb.limit(), '\n');

					if (idx == -1) {
						readNext(bb, 0);
						return;
					}

					try {
						bb.position(idx + 1);
						assert !buf.hasRemaining();
						buf = bb.hasRemaining() ? (isReadBuffer(bb) ? IoUtils.getFrom(bb) : bb) : emptyByteBuffer();
						ByteBuffer payload = getPayload();
						this.payload = null;
						done(payload);
					} catch (Throwable ex) {
						consumer.apply(null, ex).thenComplete(this);
					}

					return;
				}

				bb.position(idx + 1);
				int remain = bb.remaining();
				if (!consume(bb, Math.min(remain, (int) len))) return;

				if (remain < len) {
					readNext(bb, (int) (len - remain));
					return;
				}
			}

			readNext(bb, 0);
		}

		protected void done(ByteBuffer payload) {
			consumer.apply(payload, null).thenComplete(this);
		}

		protected boolean consume(ByteBuffer bb, int len) {
			try {
				int limit = bb.limit();
				payload = ensureCapacity((payload == null) ? emptyByteBuffer() : payload, len, maxLen);
				bb.limit(bb.position() + len);
				payload.put(bb);
				bb.limit(limit);
				return true;
			} catch (Throwable ex) {
				consumer.apply(null, ex).thenComplete(this);
				return false;
			}
		}

		protected ByteBuffer getPayload() throws IOException {
			assert payload != null;
			payload.limit(payload.position()).position(0);
			return payload;
		}

		private void readNext(ByteBuffer bb, int expectedLen) {
			try {
				this.expectedLen = expectedLen;

				if (bb.hasRemaining()) {
					lenBuf.clear();
					lenBuf.put(bb);
				} else {
					lenBuf.limit(0);
				}

				getChannel().read(this, this);
			} catch (Throwable ex) {
				consumer.apply(null, ex).thenComplete(this);
			}
		}

		@Override
		public void onCompletion(ByteBuffer result, Throwable fail) {
			if (fail != null) {
				consumer.apply(null, new IOException("Failed to read payload", fail)).thenComplete(this);
				return;
			}

			consume(result);
		}

		@Override
		public ByteBuffer getByteBuffer() {
			ByteBuffer buf = getReadBuffer();
			buf.put(lenBuf);
			return buf;
		}
	}

	private final class ChunkedEncodedPayloadPromise<T> extends ChunkedPayloadPromise<T>
			implements Completion<ByteBuffer>, ByteBufferSupplier {
		private final String enc;

		public ChunkedEncodedPayloadPromise(String enc, int maxLen,
																				BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer) {
			super(maxLen, consumer);
			this.enc = enc;
		}

		@Override
		protected ByteBuffer getPayload() throws IOException {
			return decode(super.getPayload(), enc, maxLen);
		}
	}

	private final class WritePayloadPromise extends Promise<Void> implements Completion<ByteBuffer>, ByteBufferSupplier {
		private final AsyncOutputStream out;
		private long remaining;

		public WritePayloadPromise(AsyncOutputStream out, long remaining) {
			this.out = out;
			this.remaining = remaining;
		}

		@Override
		public void onCompletion(ByteBuffer result, Throwable fail) {
			if (fail != null) {
				completeExceptionally(new IOException("Failed to read payload", fail));
				return;
			}

			int available = result.remaining();
			int limit = result.limit();
			if (available > remaining) result.limit(result.position() + (int) remaining);
			ByteBuffer src = out.isAsync() ? IoUtils.copyOf(result) : result;

			out.write(src).onCompletion((v, err) -> {
				if (err != null) {
					completeExceptionally(new IOException("Failed to write payload", err));
				} else if (available >= remaining) {
					result.limit(limit).position((int) remaining);
					assert !buf.hasRemaining();
					if (result.hasRemaining()) buf = IoUtils.getFrom(result);
					out.endOfStream();
					complete(null);
				} else {
					remaining -= available;
					getChannel().read(this, this);
				}
			});
		}

		@Override
		public ByteBuffer getByteBuffer() {
			return getReadBuffer();
		}
	}

	private final class WriteChunkedPayloadPromise extends ChunkedPayloadPromise<Void> {
		private final AsyncOutputStream out;
		private FutureSupplier<Void> writer = completedNull();

		public WriteChunkedPayloadPromise(AsyncOutputStream out) {
			super(0, (b, err) -> completedVoid());
			this.out = out;
		}

		@Override
		protected boolean consume(ByteBuffer bb, int len) {
			if (!out.isAsync()) {
				int limit = bb.limit();
				bb.limit(bb.position() + len);
				FutureSupplier<Void> w = out.write(bb);
				assert w.isDone();
				assert !bb.hasRemaining();
				bb.limit(limit);
				return !w.isFailed();
			}

			if (writer.isFailed()) return false;

			ByteBuffer src = copyOfRange(bb, bb.position(), bb.position() + len);
			bb.position(bb.position() + len);
			writer = writer.then(v -> out.write(src));
			return true;
		}

		@Override
		protected ByteBuffer getPayload() {
			return emptyByteBuffer();
		}

		@Override
		protected void done(ByteBuffer payload) {
			writer.thenRun(out::endOfStream).thenComplete(this);
		}
	}

	private final class SkipPayloadPromise extends Promise<Void> implements Completion<ByteBuffer>, ByteBufferSupplier {
		private long remaining;

		public SkipPayloadPromise(long remaining) {
			assert !buf.hasRemaining();
			this.remaining = remaining;
		}

		@Override
		public void onCompletion(ByteBuffer result, Throwable fail) {
			if (fail != null) {
				completeExceptionally(new IOException("Failed to read payload", fail));
				return;
			}

			int available = result.remaining();

			if (available >= remaining) {
				assert !buf.hasRemaining();
				result.position(result.position() + (int) remaining);
				if (result.hasRemaining()) buf = IoUtils.getFrom(result);
				complete(null);
			} else {
				remaining -= available;
				getChannel().read(this, this);
			}
		}

		@Override
		public ByteBuffer getByteBuffer() {
			return getReadBuffer();
		}
	}

	private final class SkipChunkedPayloadPromise extends ChunkedPayloadPromise<Void> {

		public SkipChunkedPayloadPromise() {
			super(0, (b, err) -> completedVoid());
		}

		@Override
		protected boolean consume(ByteBuffer bb, int len) {
			bb.position(bb.position() + len);
			return true;
		}

		@Override
		protected ByteBuffer getPayload() {
			return emptyByteBuffer();
		}
	}
}
