package me.aap.utils.io;

import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

/**
 * @author Andrey Pavlenko
 */
public class Utf8ReaderTest extends Assertions {
	private static final int[][] UNICODE_RANGES =
			new int[][]{new int[]{0x0020, 0x007F},  // Basic Latin
					new int[]{0x00A0, 0x00FF},  // Latin-1 Supplement
					new int[]{0x0100, 0x017F},  // Latin Extended-A
					new int[]{0x0180, 0x024F},  // Latin Extended-B
					new int[]{0x0250, 0x02AF},  // IPA Extensions
					new int[]{0x02B0, 0x02FF},  // Spacing Modifier Letters
					new int[]{0x0300, 0x036F},  // Combining Diacritical Marks
					new int[]{0x0370, 0x03FF},  // Greek and Coptic
					new int[]{0x10330, 0x1034F},  // Gothic
					new int[]{0xE0000, 0xE007F},  // Tags
			};
	private static final int[] UNICODE_ALPHABET;

	static {
		var len = 0;
		for (var r : UNICODE_RANGES) len += r.length;
		UNICODE_ALPHABET = new int[len];
		for (int i = 0, off = 0; i < UNICODE_RANGES.length; i++) {
			var r = UNICODE_RANGES[i];
			arraycopy(r, 0, UNICODE_ALPHABET, off, r.length);
			off += r.length;
		}
	}

	private final Random random = new Random();

	@Test
	public void testRead() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("\n\n");
		addRndText(sb, random.nextInt(1000));

		var expect = sb.toString();
		sb.setLength(0);
		try (Utf8Reader r = new Utf8Reader(new ByteArrayInputStream(expect.getBytes(UTF_8)))) {
			for (int i = r.read(); i != -1; i = r.read()) sb.appendCodePoint(i);
		}
		assertEquals(expect, sb.toString());
	}

	@Test
	public void testReadLine() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("\n\n");
		addRndText(sb, random.nextInt(1000));

		var expect = sb.toString();
		sb.setLength(0);
		try (Utf8Reader r = new Utf8Reader(new ByteArrayInputStream(expect.getBytes(UTF_8)))) {
			for (int i = r.read(); i != -1; i = r.read()) sb.appendCodePoint(i);
		}
		assertEquals(expect, sb.toString());
	}

	private void addRndText(StringBuilder sb, int lines) {
		for (int i = 0; i < lines; i++) {
			addRndString(sb, random.nextInt(1000));
			sb.append("\n");
		}
	}

	private void addRndString(StringBuilder sb, int length) {
		for (int n = sb.length() + length; sb.length() < n; ) {
			sb.appendCodePoint(UNICODE_ALPHABET[random.nextInt(UNICODE_ALPHABET.length)]);
		}
	}
}
