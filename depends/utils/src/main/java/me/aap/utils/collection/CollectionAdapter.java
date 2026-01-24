package me.aap.utils.collection;

import androidx.annotation.NonNull;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Andrey Pavlenko
 */
public abstract class CollectionAdapter<From, To> extends AbstractCollection<To> {
	private final Collection<From> collection;

	public CollectionAdapter(Collection<From> collection) {
		this.collection = collection;
	}

	protected abstract To adapt(From v);

	@NonNull
	@Override
	public Iterator<To> iterator() {
		return new IteratorAdapter<From, To>(collection.iterator()) {
			@Override
			protected To adapt(From v) {
				return CollectionAdapter.this.adapt(v);
			}
		};
	}

	@Override
	public int size() {
		return collection.size();
	}
}
