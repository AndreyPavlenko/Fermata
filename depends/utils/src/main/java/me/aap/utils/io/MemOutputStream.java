package me.aap.utils.io;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.annotation.Nonnull;

import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.io.IoUtils.emptyByteArray;

/**
 * @author Andrey Pavlenko
 */
public class MemOutputStream extends OutputStream {
	public static final int MAX_SIZE = (int) Math.min(Integer.MAX_VALUE >> 1, Runtime.getRuntime().maxMemory() / 2);
	public static final int DEFAULT_SIZE = Math.min(8192, MAX_SIZE);
	private final int init;
	private final int max;
	private byte[] buf;
	private int off;

	public MemOutputStream() {
		this(DEFAULT_SIZE);
	}

	public MemOutputStream(int init) {
		this(init, Integer.MAX_VALUE);
	}

	public MemOutputStream(int init, int max) {
		this.init = Math.min(init, max);
		this.max = max;
		buf = emptyByteArray();
	}

	public byte[] getBuffer() {
		return buf;
	}

	public ByteBuffer getByteBuffer() {
		return ByteBuffer.wrap(buf, 0, off);
	}

	public byte[] trimBuffer() {
		if (buf.length != off) buf = Arrays.copyOf(buf, off);
		return buf;
	}

	@Override
	public void write(int b) throws IOException {
		ensureCapacity(1)[off++] = (byte) b;
	}

	@Override
	public void write(@NonNull byte[] b, int off, int len) throws IOException {
		byte[] buf = ensureCapacity(len);
		System.arraycopy(b, off, buf, this.off, len);
		this.off += len;
	}

	public void write(ByteBuffer bb) throws IOException {
		int len = bb.remaining();
		bb.get(ensureCapacity(len), off, len);
		off += len;
	}

	public void writeTo(@NonNull OutputStream out) throws IOException {
		out.write(buf, 0, off);
	}

	public void readFrom(InputStream in) throws IOException {
		for (byte[] buf = this.buf; ; ) {
			if (off == buf.length) buf = ensureCapacity(Math.max(1, in.available()));
			int i = in.read(buf, off, buf.length - off);
			if (i == -1) return;
			else off += i;
		}
	}

	public FutureSupplier<MemOutputStream> readFrom(AsyncInputStream in) {
		boolean[] eos = new boolean[1];
		return Async.iterate(() -> {
					if (eos[0] || !in.hasRemaining()) return null;

					ByteBuffer buf;

					try {
						byte[] b = ensureCapacity(Math.max(1, in.available()));
						buf = ByteBuffer.wrap(b, off, b.length - off);
					} catch (IOException ex) {
						return failed(ex);
					}

					return in.read(buf).then(bb -> {
						assert bb == buf;
						int remain = bb.remaining();

						if (remain > 0) {
							off += remain;
							return completed(MemOutputStream.this);
						} else {
							eos[0] = true;
							return completed(MemOutputStream.this);
						}
					});
				}
		);
	}

	public int getCount() {
		return off;
	}

	public synchronized void reset() {
		off = 0;
	}

	@Override
	public void close() {
	}

	@Nonnull
	@Override
	public String toString() {
		return "count: " + getCount() + ", capacity: " + ((buf == null) ? 0 : buf.length);
	}

	private byte[] ensureCapacity(int n) throws IOException {
		int required = off + n;

		if ((required < 0) || (required > max)) {
			throw new IOException("Maximum buffer size exceeded: " + max);
		}

		if (required <= buf.length) return buf;

		int l = (buf.length == 0) ? init : (buf.length << 1);

		if (l < required) l = required;
		else if (l > max) l = max;

		byte[] b = new byte[l];
		System.arraycopy(buf, 0, b, 0, off);
		return buf = b;
	}
}