package me.aap.utils.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Andrey Pavlenko
 */
public class CountingOutputStream extends OutputStream {
	private final OutputStream out;
	private long count;

	public CountingOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		count++;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
		count += len;
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	public long getCount() {
		return count;
	}
}
