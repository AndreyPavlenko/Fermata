package me.aap.fermata.util;

import java.util.Comparator;

import static java.lang.Character.isDigit;

/**
 * @author Andrey Pavlenko
 */
public class NaturalOrderComparator implements Comparator<String> {

	public static int compareNatural(String a, String b) {
		int alen = a.length();
		int blen = b.length();

		for (int aidx = 0, bidx = 0; ; ) {
			if (aidx == alen) return (bidx == blen) ? 0 : -1;
			if (bidx == blen) return 1;

			char ca = a.charAt(aidx);
			char cb = b.charAt(bidx);

			if (isDigit(ca) && isDigit(cb)) {
				int aoff = aidx;
				int boff = bidx;

				for (aidx++; (aidx < alen) && isDigit(a.charAt(aidx)); aidx++) ;
				for (bidx++; (bidx < blen) && isDigit(b.charAt(bidx)); bidx++) ;

				int nalen = aidx - aoff;
				int nblen = bidx - boff;

				if ((nalen != 1) || (nblen != 1)) {
					long na = Long.parseLong(a.substring(aoff, aidx));
					long nb = Long.parseLong(b.substring(boff, bidx));

					if (na == nb) {
						if (nalen != nblen) return (nalen < nblen) ? -1 : 1;
					} else if (na < nb) {
						return -1;
					} else {
						return 1;
					}
				} else if (ca != cb) {
					return (ca < cb) ? -1 : 1;
				}
			} else if (ca != cb) {
				return (ca < cb) ? -1 : 1;
			} else {
				aidx++;
				bidx++;
			}
		}
	}

	public int compare(String a, String b) {
		return compareNatural(a, b);
	}
}

