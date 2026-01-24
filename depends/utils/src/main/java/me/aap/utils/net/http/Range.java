package me.aap.utils.net.http;


import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import static java.lang.Long.MIN_VALUE;
import static me.aap.utils.misc.Assert.assertTrue;

/**
 * @author Andrey Pavlenko
 */
public class Range {
	private final long start;
	private long end;
	private static final Range invalid = new Range(-1, -1) {

		@Override
		public void align(long length) {
		}

		@Override
		public boolean isSatisfiable(long length) {
			return false;
		}
	};

	public Range(long start, long end) {
		this.start = start;
		this.end = end;
	}

	static Range parse(ByteBuffer bytes, int off, int end) {
		if (!HttpUtils.starts(bytes, off, end, "bytes=")) return invalid;

		int idx = HttpUtils.indexOfChar(bytes, off += 6, end, "-\n\r");
		if ((idx == -1) || (bytes.get(idx) != '-')) return invalid;

		if (idx == off) {
			long rangeEnd = HttpUtils.parseLong(bytes, off, end, "\n\r", MIN_VALUE);
			assertTrue(rangeEnd < 0);
			return (rangeEnd == MIN_VALUE) ? invalid : new Range(0, rangeEnd);
		}

		long rangeEnd = 0;
		long rangeStart = HttpUtils.parseLong(bytes, off, idx, "-\n\r", MIN_VALUE);
		if (rangeStart == MIN_VALUE) return invalid;


		if ((++idx < end) && (bytes.get(idx) > ' ')) {
			rangeEnd = HttpUtils.parseLong(bytes, idx, end, "\n\r", MIN_VALUE);
			if (rangeEnd == MIN_VALUE) return invalid;
		}

		return new Range(rangeStart, rangeEnd);
	}

	public long getStart() {
		return start;
	}

	public long getEnd() {
		return end;
	}

	public void align(long length) {
		if (end < 0) {
			end = length + end;
		} else if ((end == 0) || (end >= length)) {
			end = length - 1;
		}
	}

	public boolean isSatisfiable(long length) {
		return (start < length) && (end < length) && (start <= end);
	}

	public long getLength() {
		return getEnd() - getStart() + 1;
	}

	@NonNull
	@Override
	public String toString() {
		if (start < 0) {
			return -start + "-";
		} else if (end < 0) {
			return -end + "";
		} else {
			return start + "-" + end;
		}
	}
}
