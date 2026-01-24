package me.aap.utils.net.http;

import java.nio.ByteBuffer;

import me.aap.utils.net.http.HttpMessageBase.AsciiSeq;

/**
 * @author Andrey Pavlenko
 */
class HttpUtils {

	static long parseLong(ByteBuffer bytes, int start, int end, String endChars, long invalid) {
		if ((start < 0) || (start >= end)) return invalid;

		long value = 0;
		boolean negative = false;

		if (bytes.get(start) == '-') {
			negative = true;
			start++;
		}

		for (; (start < end) && (bytes.get(start) == '0'); start++) {
			// Skip padding zero
		}

		for (; (start < end); start++) {
			char c = (char) bytes.get(start);

			if ((c >= '0') && (c <= '9')) {
				value = value * 10 + ((long) (c - '0'));
			} else if (endChars.indexOf(c) != -1) {
				return negative ? -value : value;
			} else {
				return invalid;
			}
		}

		return negative ? -value : value;
	}

	static long parseHexLong(ByteBuffer bytes, int start, int end) {
		long v = 0;
		int i = start;

		for (; i < end; i++) {
			char c = (char) (bytes.get(i) & 0xFF);
			if (HexTable.table[c] == -1) break;
			v = (v << 4) | HexTable.table[c];
		}

		assert Long.parseLong(new AsciiSeq(bytes, start, i - start).toString(), 16) == v;
		return v;
	}

	static boolean starts(ByteBuffer bytes, int start, int end, CharSequence seq) {
		int len = seq.length();
		if (len > (end - start)) return false;

		for (int i = 0; i < len; i++, start++) {
			if (bytes.get(start) != seq.charAt(i)) return false;
		}

		return true;
	}

	static int indexOfChar(ByteBuffer bytes, int start, int end, CharSequence chars) {
		for (int len = chars.length(); start < end; start++) {
			char c = (char) bytes.get(start);
			for (int n = 0; n < len; n++) {
				if (chars.charAt(n) == c) return start;
			}
		}
		return -1;
	}

	static int indexOfChar(ByteBuffer bytes, int start, int end, char c) {
		for (; start < end; start++) {
			if (c == ((char) bytes.get(start))) return start;
		}
		return -1;
	}

	private static class HexTable {
		static final long[] table = {
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
		};
	}
}
