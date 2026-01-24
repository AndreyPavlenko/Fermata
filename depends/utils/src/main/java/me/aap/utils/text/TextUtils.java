package me.aap.utils.text;

import static java.lang.Character.isLowerCase;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class TextUtils {

	public static int indexOfChar(CharSequence seq, CharSequence chars, int from, int to) {
		for (int len = chars.length(); from < to; from++) {
			char c = seq.charAt(from);
			for (int n = 0; n < len; n++) {
				if (chars.charAt(n) == c) return from;
			}
		}
		return -1;
	}

	public static int indexOf(CharSequence text, char c) {
		return indexOf(text, c, 0, text.length());
	}

	public static int indexOf(CharSequence text, char c, int fromIndex, int toIndex) {
		for (; fromIndex < toIndex; fromIndex++) {
			if (c == text.charAt(fromIndex)) return fromIndex;
		}
		return -1;
	}

	public static int indexOf(CharSequence text, CharSequence seq) {
		return indexOf(text, seq, false);
	}

	public static int indexOf(CharSequence text, CharSequence seq, boolean ignoreCase) {
		return indexOf(text, seq, 0, text.length(), ignoreCase);
	}

	public static int indexOf(CharSequence text, CharSequence seq, int fromIndex, int toIndex,
														boolean ignoreCase) {
		if ((fromIndex < 0) || (fromIndex >= toIndex)) return -1;

		int seqLen = seq.length();
		if (seqLen == 0) return fromIndex;

		int max = toIndex - seqLen;
		char first = seq.charAt(0);

		if (ignoreCase) {
			boolean low = isLowerCase(first);

			for (int i = fromIndex; i <= max; i++) {
				char c = text.charAt(i);

				if (((c == first) || ((low ? toLowerCase(c) : toUpperCase(c)) != c)) &&
						regionMatches(text, i, seq, 0, seqLen, true)) {
					return i;
				}
			}
		} else {
			for (int i = fromIndex; i <= max; i++) {
				if ((text.charAt(i) == first) && regionMatches(text, i, seq, 0, seqLen, false)) {
					return i;
				}
			}
		}

		return -1;
	}

	public static boolean regionMatches(CharSequence text, int textOffset,
																			CharSequence seq, int seqOffset, int len, boolean ignoreCase) {
		int textLen = text.length();
		int seqLen = seq.length();

		if ((seqOffset < 0) || (textOffset < 0) ||
				(textOffset > (textLen - len)) ||
				(seqOffset > (seqLen - len))) {
			return false;
		} else {
			return matches(text, seq, textOffset, seqOffset, len, ignoreCase);
		}
	}

	private static boolean matches(CharSequence s1, CharSequence s2, int off1, int off2, int len,
																 boolean ignoreCase) {
		if (!ignoreCase) {
			for (int i = 0; i < len; i++) {
				if (s1.charAt(off1 + i) != s2.charAt(off2 + i)) return false;
			}
			return true;
		}

		for (int i = 0; i < len; i++) {
			char c1 = s1.charAt(off1 + i);
			char c2 = s2.charAt(off2 + i);

			if (c1 != c2) {
				c1 = toUpperCase(c1);
				c2 = toUpperCase(c2);

				if (c1 != c2) {
					c1 = toLowerCase(c1);
					c2 = toLowerCase(c2);
					if (c1 != c2) return false;
				}
			}
		}

		return true;
	}

	public static boolean equals(CharSequence text1, CharSequence text2) {
		if (text1 == text2) {
			return true;
		} else if ((text1 != null) && (text2 != null)) {
			int len1 = text1.length();
			int len2 = text2.length();
			return (len1 == len2) && matches(text1, text2, 0, 0, len1, false);
		}

		return false;
	}

	public static boolean equals(CharSequence text1, CharSequence text2, int off1, int off2,
															 int endOff1, int endOff2) {
		int len1 = endOff1 - off1;
		int len2 = endOff2 - off2;
		return (len1 == len2) && matches(text1, text2, off1, off2, len1, false);
	}

	public static boolean startsWith(CharSequence text, CharSequence seq) {
		return startsWith(text, seq, false);
	}

	public static boolean endsWith(CharSequence text, CharSequence seq) {
		return endsWith(text, seq, false);
	}

	public static boolean startsWith(CharSequence text, CharSequence seq, boolean ignoreCase) {
		return regionMatches(text, 0, seq, 0, seq.length(), ignoreCase);
	}

	public static boolean endsWith(CharSequence text, CharSequence seq, boolean ignoreCase) {
		final int len = seq.length();
		return regionMatches(text, text.length() - len, seq, 0, len, ignoreCase);
	}

	public static String trim(String text) {
		return (text == null) ? null : text.trim();
	}

	@SuppressWarnings("StatementWithEmptyBody")
	public static CharSequence trim(CharSequence text) {
		if (text == null) return null;
		int len = text.length();
		int start = 0;
		int end = len;
		for (; (start < end) && (text.charAt(start) <= ' '); start++) ;
		for (; (start < end) && (text.charAt(end - 1) <= ' '); end--) ;
		return ((start != 0) || (end != len)) ? text.subSequence(start, end) : text;
	}

	public static long toLong(CharSequence seq, int from, int to, long defaultValue) {
		try {
			return Long.parseLong(seq.subSequence(from, to).toString());
		} catch (NumberFormatException ignore) {
			return defaultValue;
		}
	}

	public static String toString(Throwable ex) {
		try (CharArrayWriter cw = new CharArrayWriter(); PrintWriter w = new PrintWriter(cw)) {
			ex.printStackTrace(w);
			w.flush();
			return cw.toString();
		}
	}

	public static boolean isInt(CharSequence seq) {
		int len = seq.length();
		if (len > 11) return false;

		for (int i = 0; i < len; i++) {
			char c = seq.charAt(i);
			if ((c >= '0') && (c <= '9')) continue;
			if ((c == '-') && (i == 0)) continue;
			return false;
		}

		return true;
	}

	public static String timeToString(int seconds) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			timeToString(tb, seconds);
			return tb.toString();
		}
	}

	public static void timeToString(TextBuilder tb, int seconds) {
		timeToString(tb.getStringBuilder(), seconds);
	}

	public static void timeToString(StringBuilder sb, int seconds) {
		if (seconds < 60) {
			sb.append("00:");
			appendTime(sb, seconds);
		} else if (seconds < 3600) {
			int m = seconds / 60;
			appendTime(sb, m);
			sb.append(':');
			appendTime(sb, seconds - (m * 60));
		} else {
			int h = seconds / 3600;
			appendTime(sb, h);
			sb.append(':');
			timeToString(sb, seconds - (h * 3600));
		}
	}

	public static void dateToTimeString(TextBuilder tb, long date, boolean seconds) {
		dateToTimeString(tb.getStringBuilder(), date, seconds);
	}

	public static void dateToTimeString(StringBuilder sb, long date, boolean seconds) {
		Calendar c = GregorianCalendar.getInstance();
		c.setTime(new Date(date));
		int v = c.get(Calendar.HOUR_OF_DAY);
		if (v < 10) sb.append('0');
		sb.append(v).append(':');
		v = c.get(Calendar.MINUTE);
		if (v < 10) sb.append('0');
		sb.append(v);

		if (seconds) {
			sb.append(':');
			v = c.get(Calendar.SECOND);
			if (v < 10) sb.append('0');
			sb.append(v);
		}
	}

	private static void appendTime(StringBuilder sb, int time) {
		if (time < 10) sb.append(0);
		sb.append(time);
	}

	public static int stringToTime(String s) {
		String[] values = s.split(":");

		try {
			if (values.length == 2) {
				return Integer.parseInt(values[0]) * 60 + Integer.parseInt(values[1]);
			} else if (values.length == 3) {
				return Integer.parseInt(values[0]) * 3600 + Integer.parseInt(values[1]) * 60
						+ Integer.parseInt(values[2]);
			}
		} catch (NumberFormatException ex) {
			Log.w("Utils", "Invalid time string: " + s, ex);
		}

		return -1;
	}

	public static boolean containsWord(String s, String word) {
		int idx = s.indexOf(word);

		if (idx != -1) {
			if ((idx == 0) || isSepChar(s.charAt(idx - 1))) {
				idx += word.length();
				return (idx == s.length()) || ((idx < s.length()) && isSepChar(s.charAt(idx)));
			}
		}

		return false;
	}

	public static boolean isBlank(CharSequence s) {
		for (int i = 0, n = s.length(); i < n; i++) {
			if (s.charAt(i) > ' ') return false;
		}
		return true;
	}

	public static boolean isNullOrBlank(CharSequence s) {
		return (s == null) || isBlank(s);
	}

	private static boolean isSepChar(char c) {
		switch (c) {
			case '.':
			case ',':
			case '\'':
			case ';':
			case ':':
			case ')':
			case '(':
				return true;
			default:
				return c <= '"';
		}
	}

	public static byte[] toByteArray(CharSequence s) {
		return toByteArray(s, Charset.defaultCharset());
	}

	public static byte[] toByteArray(CharSequence s, Charset cs) {
		return toByteArray(s, cs, 0, s.length());
	}

	public static byte[] toByteArray(CharSequence s, Charset cs,
																	 int fromIndex, int toIndex) {
		if (s.getClass() == String.class) {
			return ((String) s).getBytes(cs);
		} else {
			CharsetEncoder ce = cs.newEncoder();
			int len = toIndex - fromIndex;
			int maxLen = (int) (len * (double) ce.maxBytesPerChar());
			byte[] ba = new byte[maxLen];
			ByteBuffer bb = ByteBuffer.wrap(ba);
			CharBuffer cb = CharBuffer.wrap(s, fromIndex, toIndex);
			ce.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);

			try {
				CoderResult cr = ce.encode(cb, bb, true);

				if (!cr.isUnderflow()) {
					cr.throwException();
				}

				if (!(cr = ce.flush(bb)).isUnderflow()) {
					cr.throwException();
				}
			} catch (CharacterCodingException x) {
				throw new Error(x);
			}

			return (bb.position() == ba.length) ? ba : Arrays.copyOf(ba, bb.position());
		}
	}

	public static String toHexString(byte[] bytes) {
		try (SharedTextBuilder tb = SharedTextBuilder.get(bytes.length * 2)) {
			return appendHexString(tb, bytes).toString();
		}
	}

	public static TextBuilder appendHexString(TextBuilder tb, byte[] bytes) {
		appendHexString(tb.getStringBuilder(), bytes);
		return tb;
	}

	public static TextBuilder appendHexString(TextBuilder tb, byte[] bytes, int start, int end) {
		appendHexString(tb.getStringBuilder(), bytes, start, end);
		return tb;
	}

	public static StringBuilder appendHexString(StringBuilder sb, byte[] bytes) {
		return appendHexString(sb, bytes, 0, bytes.length);
	}

	public static StringBuilder appendHexString(StringBuilder sb, byte[] bytes, int start, int end) {
		while (start < end) {
			int v = bytes[start++] & 0xFF;
			sb.append(HexTable.table[v >>> 4]).append(HexTable.table[v & 0xF]);
		}
		return sb;
	}

	public static byte[] hexToBytes(CharSequence hex) {
		int len = hex.length();
		byte[] bytes = new byte[len / 2];
		int n = 0;

		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) ((Character.digit(hex.charAt(n++), 16) << 4) +
					Character.digit(hex.charAt(n++), 16));
		}

		return bytes;
	}

	public static byte[] hexToLong(CharSequence hex) {
		int len = hex.length();
		byte[] bytes = new byte[len / 2];
		int n = 0;

		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) ((Character.digit(hex.charAt(n++), 16) << 4) +
					Character.digit(hex.charAt(n++), 16));
		}

		return bytes;
	}

	public static String toString(ByteBuffer b, Charset cs) {
		if (!b.hasArray()) {
			ByteBuffer bb = ByteBuffer.allocate(b.remaining());
			bb.put(b);
			b = bb;
		}
		return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), cs);
	}

	public static int compareToIgnoreCase(CharSequence s1, CharSequence s2) {
		int n1 = s1.length();
		int n2 = s2.length();

		for (int i = 0; i < Math.min(n1, n2); i++) {
			char c1 = s1.charAt(i);
			char c2 = s2.charAt(i);

			if (c1 != c2) {
				c1 = Character.toUpperCase(c1);
				c2 = Character.toUpperCase(c2);

				if (c1 != c2) {
					c1 = Character.toLowerCase(c1);
					c2 = Character.toLowerCase(c2);
					if (c1 != c2) return c1 - c2;
				}
			}
		}

		return n1 - n2;
	}

	private static class HexTable {
		static char[] table = {'0', '1', '2', '3', '4', '5', '6', '7',
				'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	}
}
