package me.aap.fermata.addon.felex.dict;

public class WordMatcher {
	public static boolean matches(DictInfo di, Translation w1, String w2) {
		return matches(di, w1.getTranslation(), w2);
	}

	public static boolean matches(DictInfo di, Word w1, String w2) {
		String word = w1.getWord();
		if (matches(di, word, w2)) return true;
		String expr = w1.getExpr();
		//noinspection StringEquality
		return (expr != word) && matches(di, expr, w2);
	}

	public static boolean matches(DictInfo di, String w1, String w2) {
		if ((w1 == null) || (w2 == null)) return false;
		var r1 = new CodePointReader(w1);
		var r2 = new CodePointReader(w2);
		for (int c1 = r1.next(), c2 = r2.next(); ; c1 = r1.next(), c2 = r2.next()) {
			if ((c1 == -1) || (c2 == -1)) return c1 == c2;
			if (!cmp(di, r1, r2, c1, c2)) return false;
		}
	}

	private static boolean cmp(DictInfo di, CodePointReader r1, CodePointReader r2, int c1, int c2) {
		if (c1 == c2) return true;
		c1 = Character.toLowerCase(c1);
		if (c1 == c2) return true;
		c2 = Character.toLowerCase(c2);
		if (c1 == c2) return true;

		String m = di.getCharMap(c1);
		if (m != null) {
			switch (m.codePointCount(0, m.length())) {
				case 1 -> {
					return c2 == m.codePointAt(0);
				}
				case 2 -> {
					return (c2 == m.codePointAt(0)) && (Character.toLowerCase(r2.next()) == m.codePointAt(1));
				}
			}
			return false;
		}
		m = di.getCharMap(c2);
		if (m == null) return false;
		switch (m.codePointCount(0, m.length())) {
			case 1 -> {
				return c1 == m.codePointAt(0);
			}
			case 2 -> {
				return (c1 == m.codePointAt(0)) && (Character.toLowerCase(r1.next()) == m.codePointAt(1));
			}
		}
		return false;
	}

	private static final class CodePointReader {
		private final String str;
		private final int len;
		private int off;

		private CodePointReader(String str) {
			this.str = str;
			len = str.length();
		}

		int next() {
			while (off < len) {
				int cp = str.codePointAt(off++);
				switch (cp) {
					case '.', ',', '?', '!', ':', ';', '"', '\'', '/', '\\', '(', ')' -> {
						// ignore
					}
					case ' ' -> {
						for (; (off < len) && (str.codePointAt(off) == ' '); off++) ;
					}
					default -> {
						return Character.toUpperCase(cp);
					}
				}
			}
			return -1;
		}
	}
}
