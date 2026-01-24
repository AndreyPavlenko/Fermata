package me.aap.utils.net.http;

import java.nio.ByteBuffer;

import me.aap.utils.BuildConfig;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.concurrent.NetThread;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;
import me.aap.utils.net.ByteBufferSupplier;
import me.aap.utils.net.NetChannel;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.utils.io.IoUtils.copyOfRange;
import static me.aap.utils.io.IoUtils.emptyByteBuffer;

/**
 * @author Andrey Pavlenko
 */
abstract class HttpMessageEncoder<M extends HttpMessage> implements ByteBufferSupplier {
	static final byte[] H_CONNECTION = "oOnNnNeEcCtTiIoOnN".getBytes(US_ASCII);
	static final byte[] H_CONNECTION_CLOSE = "cClLoOsSeE".getBytes(US_ASCII);
	static final byte[] H_CONNECTION_KEEP = "KkeEeEpP--AalLiIvVeE".getBytes(US_ASCII);
	static final byte[] H_CONTENT = "oOnNtTeEnNtT--".getBytes(US_ASCII);
	static final byte[] H_CONTENT_ENCODING = "EeNnCcOoDdIiNnGg".getBytes(US_ASCII);
	static final byte[] H_CONTENT_LEN = "LleEnNgGtThH".getBytes(US_ASCII);
	static final byte[] H_CONTENT_TYPE = "TtYyPpEe".getBytes(US_ASCII);
	static final byte[] H_TRANSFER_ENCODING = "rRaAnNsSfFeErF--EenNcCoOdDiInNgG".getBytes(US_ASCII);

	abstract boolean encodeMessage(NetChannel channel, ByteBuffer bb, Throwable fail);

	void readMessage(NetChannel channel) {
		channel.read(this, (bb, fail) -> {
			if (BuildConfig.D && (bb != null)) assertLocalBuffer(bb);
			read(channel, bb, fail);
		});
	}

	void read(NetChannel channel, ByteBuffer bb, Throwable fail) {
		for (; ; ) {
			if (!encodeMessage(channel, bb, fail) || !channel.isOpen()) return;

			if (!bb.hasRemaining()) {
				readMessage(channel);
				return;
			}
		}
	}

	void incompleteMessage(NetChannel channel, ByteBuffer buf, int start, int end) {
		if ((start == 0) && (end == getMaxLen())) {
			onMessageTooLong(channel);
		} else {
			channel.read(retainBuf(buf, start, end), (b, f) -> read(channel, b, f));
		}
	}

	void onFailure(NetChannel channel, Throwable fail) {
		channel.close();
		Log.d(fail, "Failed to encode message");
	}

	void onMessageTooLong(NetChannel channel) {
		Log.e("HttpMessage is too long");
		channel.close();
	}

	@Override
	public ByteBuffer getByteBuffer() {
		return NetThread.getReadBuffer();
	}

	@Override
	public ByteBufferSupplier retainByteBuffer(ByteBuffer bb) {
		throw new RuntimeException("Unable to retain thread local ByteBuffer");
	}

	@Override
	public void releaseByteBuffer(ByteBuffer bb) {
		if (BuildConfig.D) {
			assertLocalBuffer(bb);
		}
	}

	int getMaxLen() {
		return 4096;
	}

	void assertLocalBuffer(ByteBuffer bb) {
		NetThread.assertReadBuffer(bb);
	}

	static <T extends HttpMessageBase> int encodeHeaderC(T m, ByteBuffer buf, int i, int end) {
		int value = headerMatch(H_CONNECTION, buf, i + 1, end);

		if (value < 0) {
			if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
			i = -value;
			value = valueMatch(H_CONNECTION_CLOSE, buf, i, end);

			if (value < 0) {
				if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
				m.connectionClose = 1;
			} else {
				value = valueMatch(H_CONNECTION_KEEP, buf, i, end);

				if (value < 0) {
					if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
					m.connectionClose = 2;
				}
			}

			return value;
		}

		value = headerPrefixMatch(H_CONTENT, buf, i + 1, end);

		if (value < 0) {
			if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;

			i = -value;
			value = headerMatch(H_CONTENT_LEN, buf, i, end);

			if (value < 0) {
				if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
				m.contentLen = value + m.headerStart;
				return value;
			}

			value = headerMatch(H_CONTENT_ENCODING, buf, i, end);

			if (value < 0) {
				if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
				m.contentEncodingStart = -(value + m.headerStart);
				return value;
			}

			value = headerMatch(H_CONTENT_TYPE, buf, i, end);

			if (value < 0) {
				if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
				m.contentTypeStart = -(value + m.headerStart);
				return value;
			}
		}

		return value;
	}

	static <T extends HttpMessageBase> int encodeHeaderT(T m, ByteBuffer buf, int i, int end) {
		int value = headerMatch(H_TRANSFER_ENCODING, buf, i + 1, end);

		if (value < 0) {
			if (value == Integer.MIN_VALUE) return Integer.MIN_VALUE;
			m.transferEncodingStart = -(value + m.headerStart);
			return value;
		}

		return value;
	}

	static int headerPrefixMatch(byte[] header, ByteBuffer buf, int start, int end) {
		for (int h = 0; (start < end) && (h < header.length); start++, h += 2) {
			byte c = buf.get(start);
			if ((c != header[h]) && (c != header[h + 1])) return start;
		}

		return (start != end) ? -start : Integer.MIN_VALUE;
	}

	static int headerMatch(byte[] header, ByteBuffer buf, int start, int end) {
		byte c;

		for (int h = 0; (start < end) && (h < header.length); start++, h += 2) {
			c = buf.get(start);
			if ((c != header[h]) && (c != header[h + 1])) return start;
		}

		if (start == end) return Integer.MIN_VALUE;
		if (buf.get(start) != ':') return start;
		if (++start == end) return Integer.MIN_VALUE;
		if (((c = buf.get(start)) != ' ') && (c != '\t')) return start;

		for (start++; start < end; start++) {
			c = buf.get(start);
			if ((c != ' ') && (c != '\t')) {
				return ((c != '\r') && (c != '\n')) ? -start : start;
			}
		}

		return Integer.MIN_VALUE;
	}

	static int valueMatch(byte[] value, ByteBuffer buf, int start, int end) {
		for (int h = 0; (start < end) && (h < value.length); start++, h += 2) {
			byte c = buf.get(start);
			if ((c != value[h]) && (c != value[h + 1])) return start;
		}

		return (start != end) ? -start : Integer.MIN_VALUE;
	}

	ByteBufferSupplier retainBuf(ByteBuffer buf, int start, int end) {
		ByteBuffer b = copyOfRange(buf, start, end);
		// Log.d("Retaining buffer: ", b);

		return () -> {
			b.position(0);
			ByteBuffer bb = NetThread.getReadBuffer();
			if (bb.remaining() < b.remaining()) bb = ByteBuffer.allocate(getMaxLen());
			bb.put(b);
			return bb;
		};
	}

	static <T extends HttpMessageBase> void initMessage(T m, ByteBuffer bb) {
		m.buf = bb;
		m.headerEnd = bb.position();
		long contentLen = m.getContentLength();

		if (contentLen > 0) {
			if (bb.remaining() >= contentLen) bb.position((int) (bb.position() + contentLen));
			else bb.position(bb.limit());
		}

		m.payloadEnd = bb.position();
	}

	<T extends HttpMessageBase> boolean handleResult(NetChannel channel, ByteBuffer bb, T m,
																									 Function<T, FutureSupplier<?>> handler,
																									 boolean readNext) {
		initMessage(m, bb);
		FutureSupplier<?> result = handler.apply(m);
		m.release();

		if (result.isDone()) {
			if (result.isFailed()) {
				onFailure(channel, result.getFailure());
				return false;
			}

			if (m.isConnectionClose() || !channel.isOpen()) return false;

			if (bb.hasRemaining()) {
				assert (bb == m.buf) || (m.buf == emptyByteBuffer());

				if (!readNext) {
					// The remaining will be consumed by {@link #read(NetChannel, ByteBuffer, Throwable)}
					return true;
				}

				read(channel, bb, null);
				return false;
			} else if (m.buf.hasRemaining()) {
				assert m.buf != bb;
				read(channel, m.buf, null);
				return false;
			}

			if (readNext) {
				readMessage(channel);
				return false;
			}

			return true;
		} else {
			int remain = bb.remaining();
			ByteBufferSupplier bbs = (remain == 0) ? null : retainBuf(bb, bb.position(), bb.limit());

			result.onCompletion((r, err) -> {
				if (err != null) {
					onFailure(channel, err);
				} else if (bbs != null) {
					assert (m.buf == bb) || !m.buf.hasRemaining();
					ByteBuffer b = bbs.getByteBuffer();
					b.position(0).limit(remain);
					read(channel, b, null);
				} else if (channel.isOpen()) {
					if ((m.buf != bb) && m.buf.hasRemaining()) read(channel, m.buf, null);
					else readMessage(channel);
				}
			});

			return false;
		}
	}
}
