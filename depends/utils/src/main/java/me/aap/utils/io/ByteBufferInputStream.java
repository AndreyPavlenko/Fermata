package me.aap.utils.io;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * @author Andrey Pavlenko
 */
public class ByteBufferInputStream extends InputStream {
	private final ByteBuffer _buf;

	public ByteBufferInputStream(ByteBuffer buf) {
		_buf = buf;
	}

	@Override
	public int read() {
		return _buf.hasRemaining() ? _buf.get() & 0xFF : -1;
	}

	@Override
	public int read(byte[] bytes, int off, int len) {
		if (_buf.hasRemaining()) {
			len = Math.min(len, _buf.remaining());
			_buf.get(bytes, off, len);
			return len;
		} else {
			return -1;
		}
	}
}