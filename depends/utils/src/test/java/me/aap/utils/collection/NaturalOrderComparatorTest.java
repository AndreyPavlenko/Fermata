package me.aap.utils.collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Pavlenko
 */
public class NaturalOrderComparatorTest extends Assertions {

	@Test
	public void testCompareNatural() {
		assertTrue(NaturalOrderComparator.compareNatural("", "") == 0);
		assertTrue(NaturalOrderComparator.compareNatural("a", "a") == 0);
		assertTrue(NaturalOrderComparator.compareNatural("a", "A") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("010", "010") == 0);
		assertTrue(NaturalOrderComparator.compareNatural("0", "00") < 0);
		assertTrue(NaturalOrderComparator.compareNatural("a0", "a00") < 0);
		assertTrue(NaturalOrderComparator.compareNatural("a00a", "a0b") < 0);
		assertTrue(NaturalOrderComparator.compareNatural("a011a", "a01b") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("", "a") < 0);
		assertTrue(NaturalOrderComparator.compareNatural("z", "a") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("a1b", "a2") < 0);
		assertTrue(NaturalOrderComparator.compareNatural("a11", "a2bbb") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("a011", "a0002bbb") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("a" + Long.MAX_VALUE, "a" + (Long.MAX_VALUE - 1) + "b") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("a" + Long.MAX_VALUE + "1b", "a" + Long.MAX_VALUE + "b") > 0);
		assertTrue(NaturalOrderComparator.compareNatural("a" + Long.MAX_VALUE + "123b", "a" + Long.MAX_VALUE + "123b") == 0);
	}

	@Test
	public void testCompareNaturalIgnoreCase() {
		assertTrue(NaturalOrderComparator.compareNatural("A", "a", true) == 0);
		assertTrue(NaturalOrderComparator.compareNatural("A0", "a00", true) < 0);
		assertTrue(NaturalOrderComparator.compareNatural("a00a", "A0B", true) < 0);
		assertTrue(NaturalOrderComparator.compareNatural("A011A", "a01b", true) > 0);
		assertTrue(NaturalOrderComparator.compareNatural("", "A", true) < 0);
		assertTrue(NaturalOrderComparator.compareNatural("z", "A", true) > 0);
		assertTrue(NaturalOrderComparator.compareNatural("A1B", "a2", true) < 0);
		assertTrue(NaturalOrderComparator.compareNatural("a11", "a2BBb", true) > 0);
		assertTrue(NaturalOrderComparator.compareNatural("A011", "A0002BbB", true) > 0);
	}
}
