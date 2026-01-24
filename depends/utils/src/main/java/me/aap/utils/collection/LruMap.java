package me.aap.utils.collection;

import java.util.LinkedHashMap;

/**
 * @author Andrey Pavlenko
 */
public class LruMap<K, V> extends LinkedHashMap<K, V> {
	private final int maxSize;


	public LruMap(int maxSize) {
		this(maxSize, 10, 0.75f, true);
	}

	public LruMap(int maxSize, int initialCapacity, float loadFactor, boolean accessOrder) {
		super(initialCapacity, loadFactor, accessOrder);
		this.maxSize = maxSize;
	}

	@Override
	protected boolean removeEldestEntry(Entry<K, V> eldest) {
		return size() > maxSize;
	}
}
