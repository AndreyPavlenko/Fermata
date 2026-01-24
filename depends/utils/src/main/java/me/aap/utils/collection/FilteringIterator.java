package me.aap.utils.collection;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Andrey Pavlenko
 */
public abstract class FilteringIterator<E> implements Iterator<E> {
	private final Iterator<E> iterator;
	private E next;
	boolean hasNext = true;

	public FilteringIterator(Iterator<E> _it) {
		this.iterator = _it;
		next = findNext();
	}

	protected abstract boolean accept(E e);

	@Override
	public boolean hasNext() {
		return hasNext;
	}

	@Override
	public E next() {
		if (hasNext()) {
			E next = this.next;
			this.next = findNext();
			return next;
		} else {
			throw new NoSuchElementException();
		}
	}

	@Override
	public void remove() {
		iterator.remove();
	}

	E findNext() {
		while (iterator.hasNext()) {
			E next = iterator.next();
			if (accept(next)) return next;
		}

		hasNext = false;
		return null;
	}
}
