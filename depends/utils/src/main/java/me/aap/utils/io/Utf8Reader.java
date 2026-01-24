package me.aap.utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * @author Andrey Pavlenko
 */
public class Utf8Reader extends Reader {
	// @formatter:off
	// https://bjoern.hoehrmann.de/utf-8/decoder/dfa/
	private static final int[] table = {
			// The first part of the table maps bytes to character classes that
			// to reduce the size of the transition table and create bitmasks.
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,  0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,  0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,  0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,  0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,  9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,
			7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,  7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
			8,8,2,2,2,2,2,2,2,2,2,2,2,2,2,2,  2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,
			10,3,3,3,3,3,3,3,3,3,3,3,3,4,3,3, 11,6,6,6,5,8,8,8,8,8,8,8,8,8,8,8,

			// The second part is a transition table that maps a combination
			// of a state of the automaton and a character class to a state.
			0,12,24,36,60,96,84,12,12,12,48,72, 12,12,12,12,12,12,12,12,12,12,12,12,
			12, 0,12,12,12,12,12, 0,12, 0,12,12, 12,24,12,12,12,12,12,24,12,24,12,12,
			12,12,12,12,12,12,12,24,12,12,12,12, 12,24,12,12,12,12,12,12,12,24,12,12,
			12,12,12,12,12,12,12,36,12,36,12,12, 12,36,12,12,12,12,12,36,12,36,12,12,
			12,36,12,12,12,12,12,12,12,12,12,12,
	};
	// @formatter:on
	private static final int UTF8_ACCEPT = 0;
	private static final int UTF8_REJECT = 12;
	private final InputStream in;
	private long bytesRead;

	public Utf8Reader(InputStream in) {
		this.in = in;
	}

	@Override
	public int read() throws IOException {
		int c = 0;
		int state = 0;

		do {
			int b = in.read();
			if (b == -1) return -1;

			bytesRead++;
			int type = table[b];
			if (state == UTF8_ACCEPT) c = (0xff >> type) & b;
			else if (state == UTF8_REJECT) return '▓';
			else c = (b & 0x3f) | (c << 6);
			state = table[256 + state + type];
		} while (state != UTF8_ACCEPT);

		return c;
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int n = 0;
		for (; n < len; n++) {
			int c = read();
			if (c == -1) return (n == 0) ? -1 : n;
			cbuf[off++] = (char) c;
		}
		return n;
	}

	public InputStream getStream() {
		return in;
	}

	public long getBytesRead() {
		return bytesRead;
	}

	public void setBytesRead(long bytesRead) {
		this.bytesRead = bytesRead;
	}

	public void close() throws IOException {
		in.close();
	}
}
