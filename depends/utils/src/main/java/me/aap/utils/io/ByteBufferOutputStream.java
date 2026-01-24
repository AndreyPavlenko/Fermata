package me.aap.utils.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import static me.aap.utils.io.IoUtils.ensureCapacity;

/**
 * @author Andrey Pavlenko
 */
public class ByteBufferOutputStream extends OutputStream {
	private final int maxLen;
	private ByteBuffer buf;

	public ByteBufferOutputStream(ByteBuffer buf) {
		this(buf, Integer.MAX_VALUE);
	}

	public ByteBufferOutputStream(ByteBuffer buf, int maxLen) {
		this.maxLen = maxLen;
		this.buf = buf;
	}

	public ByteBuffer getBuffer() {
		return buf;
	}

	@Override
	public void write(int b) throws IOException {
		try {
			ensureCapacity(buf, 1, maxLen);
			buf.put((byte) b);
		} catch (BufferOverflowException ex) {
			throw new IOException(ex);
		}
	}

	@Override
	public void write(byte[] bytes, int off, int len)
			throws IOException {
		try {
			buf = ensureCapacity(buf, len, maxLen);
			buf.put(bytes, off, len);
		} catch (BufferOverflowException ex) {
			throw new IOException(ex);
		}
	}
}
