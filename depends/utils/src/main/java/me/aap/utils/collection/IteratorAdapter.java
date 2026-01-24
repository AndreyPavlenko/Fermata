package me.aap.utils.collection;

import java.util.Iterator;

/**
 * @author Andrey Pavlenko
 */
public abstract class IteratorAdapter<From, To> implements Iterator<To> {
	private final Iterator<From> iterator;

	public IteratorAdapter(Iterator<From> iterator) {
		this.iterator = iterator;
	}

	protected abstract To adapt(From v);

	@Override
	public boolean hasNext() {
		return iterator.hasNext();
	}

	@Override
	public To next() {
		return adapt(iterator.next());
	}

	@Override
	public void remove() {
		iterator.remove();
	}
}
