package me.aap.utils.collection;

import androidx.annotation.NonNull;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Andrey Pavlenko
 */
public abstract class SetAdapter<From, To> extends AbstractSet<To> {
	private final Set<From> set;

	public SetAdapter(Set<From> set) {
		this.set = set;
	}

	protected abstract To adapt(From v);

	@NonNull
	@Override
	public Iterator<To> iterator() {
		return new IteratorAdapter<From, To>(set.iterator()) {
			@Override
			protected To adapt(From v) {
				return SetAdapter.this.adapt(v);
			}
		};
	}

	@Override
	public int size() {
		return set.size();
	}
}
