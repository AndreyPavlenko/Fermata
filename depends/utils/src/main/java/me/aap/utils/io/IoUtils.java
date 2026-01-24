package me.aap.utils.io;

import static me.aap.utils.async.Completed.completedVoid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class IoUtils {

	public static void close(AutoCloseable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception ex) {
				Log.d(ex, "Failed to close ", c);
			}
		}
	}

	public static void close(AutoCloseable... closeables) {
		if (closeables != null) {
			for (AutoCloseable c : closeables) {
				close(c);
			}
		}
	}

	public static void close(Iterable<? extends AutoCloseable> closeables) {
		if (closeables != null) {
			for (AutoCloseable c : closeables) {
				close(c);
			}
		}
	}

	public static long skip(InputStream in, long n) throws IOException {
		long skipped = 0;
		while (skipped < n) {
			long s = in.skip(n - skipped);
			if (s <= 0) break;
			skipped += s;
		}
		return skipped;
	}

	public static long calculateLen(InputStream in) throws IOException {
		try {
			return skip(in, Long.MAX_VALUE);
		} finally {
			close(in);
		}
	}

	public static void writeToStream(ByteBuffer src, OutputStream out) throws IOException {
		int len = src.remaining();

		if (src.hasArray()) {
			byte[] a = src.array();
			out.write(a, src.arrayOffset() + src.position(), len);
			src.position(src.position() + len);
		} else {
			byte[] a = new byte[Math.min(len, 8192)];

			for (int l = a.length; l > 0; l = Math.min(src.remaining(), a.length)) {
				src.get(a, 0, l);
				out.write(a, 0, l);
			}
		}
	}

	public static byte[] emptyByteArray() {
		return Empty.array;
	}

	public static ByteBuffer emptyByteBuffer() {
		return Empty.bb;
	}

	public static ByteBuffer[] emptyByteBufferArray() {
		return Empty.bbArray;
	}

	public static OutputStream nullOutputStream() {
		return NullOutputStream.instance;
	}

	public static Writer nullWriter() {
		return NullWriter.instance;
	}

	public static AsyncOutputStream nullAsyncOutputStream() {
		return NullAsyncOutputStream.instance;
	}

	public static ByteBuffer ensureCapacity(ByteBuffer buf, int len, int max) throws BufferOverflowException {
		int pos = buf.position();
		int capacity = buf.capacity();
		int required = pos + len;

		if ((required < 0) || (required > max)) throw new BufferOverflowException();
		if (required <= capacity) return buf;

		int newCapacity = Math.max(required, capacity << 1);
		if (newCapacity > max) newCapacity = max;

		ByteBuffer b = ByteBuffer.allocate(newCapacity);
		buf.position(0).limit(pos);
		b.put(buf);
		return b;
	}

	public static ByteBuffer copyOf(ByteBuffer bb) {
		return copyOfRange(bb, bb.position(), bb.limit());
	}

	public static ByteBuffer copyOfRange(ByteBuffer bb, int from, int to) {
		return copyOfRange(bb, from, to, to - from);
	}

	public static ByteBuffer copyOfRange(ByteBuffer bb, int from, int to, int capacity) {
		if (capacity == 0) return emptyByteBuffer();
		int len = to - from;
		byte[] a = new byte[capacity];

		if (len > 0) {
			ByteBuffer d = bb.duplicate();
			d.position(from);
			d.limit(to);
			d.get(a, 0, len);
			bb = ByteBuffer.wrap(a);
			bb.limit(len);
		} else {
			bb = ByteBuffer.wrap(a);
			bb.limit(0);
		}

		return bb;
	}

	public static ByteBuffer getFrom(ByteBuffer bb) {
		return getRangeFrom(bb, bb.position(), bb.limit());
	}

	public static ByteBuffer getRangeFrom(ByteBuffer bb, int from, int to) {
		return getRangeFrom(bb, from, to, to - from);
	}

	public static ByteBuffer getRangeFrom(ByteBuffer bb, int from, int to, int capacity) {
		if (capacity == 0) return emptyByteBuffer();
		int len = to - from;
		byte[] a = new byte[capacity];

		if (len > 0) {
			bb.position(from);
			bb.limit(to);
			bb.get(a, 0, len);
			bb = ByteBuffer.wrap(a);
			bb.limit(len);
		} else {
			bb = ByteBuffer.wrap(a);
			bb.limit(0);
		}

		return bb;
	}

	private interface Empty {
		byte[] array = new byte[0];
		ByteBuffer bb = ByteBuffer.allocate(0);
		ByteBuffer[] bbArray = new ByteBuffer[0];
	}

	private static final class NullOutputStream extends OutputStream {
		static final NullOutputStream instance = new NullOutputStream();

		@Override
		public void write(int b) {
		}

		@Override
		public void write(byte[] b, int off, int len) {
		}
	}

	private static final class NullAsyncOutputStream implements AsyncOutputStream {
		private static final NullAsyncOutputStream instance = new NullAsyncOutputStream();

		@Override
		public FutureSupplier<Void> write(ByteBuffer src) {
			src.position(src.limit());
			return completedVoid();
		}

		@Override
		public OutputStream asOutputStream() {
			return nullOutputStream();
		}

		@Override
		public boolean isAsync() {
			return false;
		}

		@Override
		public void close() {
		}
	}

	private static final class NullWriter extends Writer {
		static final NullWriter instance = new NullWriter();

		@Override
		public Writer append(CharSequence csq) throws IOException {
			return this;
		}

		@Override
		public Writer append(CharSequence csq, int start, int end) throws IOException {
			return this;
		}

		@Override
		public void write(int c) throws IOException {
		}

		@Override
		public void write(char[] chars, int i, int i1) throws IOException {
		}

		@Override
		public void write(String str) throws IOException {
		}

		@Override
		public void write(String str, int off, int len) throws IOException {
		}

		@Override
		public void flush() throws IOException {
		}

		@Override
		public void close() throws IOException {
		}
	}
}
