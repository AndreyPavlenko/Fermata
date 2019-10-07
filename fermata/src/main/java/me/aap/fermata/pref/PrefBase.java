package me.aap.fermata.pref;

import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public class PrefBase<S> implements PreferenceStore.Pref<S> {
	private final String name;
	private final S defaultValue;

	public PrefBase(String name, S defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public S getDefaultValue() {
		return defaultValue;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this) return true;
		else if (obj instanceof PreferenceStore.Pref)
			return getName().equals(((PreferenceStore.Pref<?>) obj).getName());
		else return false;
	}

	public PreferenceStore.Pref<S> withInheritance(boolean inheritable) {
		return new PrefBase<S>(getName(), getDefaultValue()) {
			@Override
			public boolean isInheritable() {
				return inheritable;
			}
		};
	}

	public PreferenceStore.Pref<S> withDefaultValue(S defaultValue) {
		return new PrefBase<>(getName(), defaultValue).withInheritance(isInheritable());
	}
}
