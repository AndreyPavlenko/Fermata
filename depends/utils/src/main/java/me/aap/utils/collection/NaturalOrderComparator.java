package me.aap.utils.collection;

import java.util.Comparator;

/**
 * @author Andrey Pavlenko
 */
public class NaturalOrderComparator implements Comparator<String> {

	public static int compareNatural(String a, String b) {
		return compareNatural(a, b, false);
	}

	public static int compareNatural(String a, String b, boolean reverse, boolean ignoreCase) {
		return reverse ? compareNatural(b, a, ignoreCase) : compareNatural(a, b, ignoreCase);
	}

	@SuppressWarnings("StatementWithEmptyBody")
	public static int compareNatural(String a, String b, boolean ignoreCase) {
		int alen = a.codePointCount(0, a.length());
		int blen = b.codePointCount(0, b.length());

		for (int aidx = 0, bidx = 0; ; ) {
			if (aidx == alen) return (bidx == blen) ? Integer.compare(alen, blen) : -1;
			if (bidx == blen) return 1;

			int ac = a.codePointAt(aidx);
			int bc = b.codePointAt(bidx);

			if (Character.isDigit(ac) && Character.isDigit(bc)) {
				// Skip zeros
				for (; (aidx < alen) && (Character.digit(a.codePointAt(aidx), 10) == 0); aidx++) ;
				for (; (bidx < blen) && (Character.digit(b.codePointAt(bidx), 10) == 0); bidx++) ;

				int anlen = 0;
				int bnlen = 0;

				// Calculate length of each number
				for (int i = aidx; (i < alen) && Character.isDigit(a.codePointAt(i)); anlen++, i++) ;
				for (int i = bidx; (i < blen) && Character.isDigit(b.codePointAt(i)); bnlen++, i++) ;

				if (anlen == bnlen) {
					for (int i = 0; i < anlen; i++, aidx++, bidx++) {
						int ad = Character.digit(a.codePointAt(aidx), 10);
						int bd = Character.digit(b.codePointAt(bidx), 10);
						if (ad != bd) return (ad < bd) ? -1 : 1;
					}
				} else {
					return (anlen < bnlen) ? -1 : 1;
				}
			} else if (ac == bc) {
				aidx++;
				bidx++;
			} else if (ignoreCase) {
				int au = Character.toUpperCase(ac);
				int bu = Character.toUpperCase(bc);
				if (au != bu) return (au < bu) ? -1 : 1;
				aidx++;
				bidx++;
			} else {
				return (ac < bc) ? -1 : 1;
			}
		}
	}

	public int compare(String a, String b) {
		return compareNatural(a, b);
	}
}

