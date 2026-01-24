package me.aap.utils.io;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * @author Andrey Pavlenko
 */
public interface RandomAccessChannel extends Closeable {

	int read(ByteBuffer dst, long position) throws IOException;

	int write(ByteBuffer src, long position) throws IOException;

	long transferFrom(ReadableByteChannel src, long position, long count) throws IOException;

	long transferTo(long position, long count, WritableByteChannel target) throws IOException;

	default long transferFrom(RandomAccessChannel src, long srcPos, long pos, long count)
			throws IOException {
		ByteBuffer bb = ByteBuffer.allocate((int) Math.min(count, 8192));
		long n = 0;

		while (n < count) {
			bb.clear();
			int i = src.read(bb, srcPos);
			if (i == -1) break;
			bb.flip();
			n += i;
			srcPos += i;
			while (bb.hasRemaining()) pos += write(bb, pos);
		}

		return n;
	}

	default long transferTo(long pos, long targetPos, long count, RandomAccessChannel target)
			throws IOException {
		return target.transferFrom(this, pos, targetPos, count);
	}

	RandomAccessChannel truncate(long size) throws IOException;

	default ByteBuffer map() throws IOException {
		return map("r");
	}

	default ByteBuffer map(String mode) throws IOException {
		return map(mode, 0, size());
	}

	ByteBuffer map(String mode, long pos, long size) throws IOException;

	default InputStream getInputStream(long pos) {
		return getInputStream(pos, Long.MAX_VALUE);
	}

	default InputStream getInputStream(long pos, long size) {
		return getInputStream(pos, size, ByteBuffer.allocate(8192));
	}

	default InputStream getInputStream(long pos, long size, ByteBuffer buf) {
		buf.limit(0);
		return new InputStream() {
			private long offset = pos;
			private long remain = size;

			@Override
			public int read() throws IOException {
				fill();
				if (remain == 0) return -1;
				offset++;
				remain--;
				return buf.get() & 0xFF;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				fill();
				if (remain == 0) return -1;
				len = Math.min(len, buf.remaining());
				buf.get(b, off, len);
				offset += len;
				remain -= len;
				return len;
			}

			private void fill() throws IOException {
				if (buf.hasRemaining()) return;
				buf.position(0).limit((int) Math.min(remain, buf.capacity()));
				if (RandomAccessChannel.this.read(buf, offset) == -1) {
					remain = 0;
					buf.limit(0);
				} else {
					buf.flip();
				}
			}
		};
	}

	default OutputStream getOutputStream(long pos) {
		return new OutputStream() {
			private long offset = pos;

			@Override
			public void write(int b) throws IOException {
				for (ByteBuffer bb = ByteBuffer.wrap(new byte[]{(byte) (0xFF & b)}); ; ) {
					if (RandomAccessChannel.this.write(bb, offset) != 0) {
						offset++;
						break;
					}
				}
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				for (ByteBuffer bb = ByteBuffer.wrap(b, off, len); bb.hasRemaining(); ) {
					offset += RandomAccessChannel.this.write(bb, offset);
				}
			}
		};
	}

	long size();

	@Override
	void close();

	default void close(boolean force) {
		close();
	}

	static RandomAccessChannel wrap(FileChannel rw, Closeable... close) {
		return wrap(rw, rw, close);
	}

	static RandomAccessChannel wrap(@Nullable FileChannel read, @Nullable FileChannel write,
																	Closeable... close) {
		return new RandomAccessFileChannelWrapper(read, write, close);
	}
}
