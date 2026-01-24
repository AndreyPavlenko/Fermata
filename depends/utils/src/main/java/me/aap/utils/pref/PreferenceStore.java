package me.aap.utils.pref;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public interface PreferenceStore extends EventBroadcaster<PreferenceStore.Listener> {

	boolean getBooleanPref(Pref<? extends BooleanSupplier> pref);

	int getIntPref(Pref<? extends IntSupplier> pref);

	int[] getIntArrayPref(Pref<? extends Supplier<int[]>> pref);

	long getLongPref(Pref<? extends LongSupplier> pref);

	long[] getLongArrayPref(Pref<? extends Supplier<long[]>> pref);

	float getFloatPref(Pref<? extends DoubleSupplier> pref);

	String getStringPref(Pref<? extends Supplier<String>> pref);

	String[] getStringArrayPref(Pref<? extends Supplier<String[]>> pref);

	default <T> T getCompoundPref(Pref<? extends Compound<T>> pref) {
		return pref.getDefaultValue().get(this);
	}

	default boolean hasPref(Pref<?> pref) {
		return hasPref(pref, true);
	}

	boolean hasPref(Pref<?> pref, boolean checkParent);

	Edit editPreferenceStore(boolean removeDefault);

	default Edit editPreferenceStore() {
		return editPreferenceStore(true);
	}

	@NonNull
	default PreferenceStore getRootPreferenceStore() {
		return this;
	}

	@Nullable
	default PreferenceStore getParentPreferenceStore() {
		return null;
	}

	default void applyBooleanPref(Pref<? extends BooleanSupplier> pref, boolean value) {
		applyBooleanPref(true, pref, value);
	}

	default void applyBooleanPref(boolean removeDefault, Pref<? extends BooleanSupplier> pref, boolean value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setBooleanPref(pref, value);
		}
	}

	default void applyIntPref(Pref<? extends IntSupplier> pref, int value) {
		applyIntPref(true, pref, value);
	}

	default void applyIntPref(boolean removeDefault, Pref<? extends IntSupplier> pref, int value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setIntPref(pref, value);
		}
	}

	default void applyIntArrayPref(Pref<? extends Supplier<int[]>> pref, int[] value) {
		applyIntArrayPref(true, pref, value);
	}

	default void applyIntArrayPref(boolean removeDefault, Pref<? extends Supplier<int[]>> pref, int[] value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setIntArrayPref(pref, value);
		}
	}

	default void applyLongPref(Pref<? extends LongSupplier> pref, long value) {
		applyLongPref(true, pref, value);
	}

	default void applyLongPref(boolean removeDefault, Pref<? extends LongSupplier> pref, long value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setLongPref(pref, value);
		}
	}

	default void applyFloatPref(Pref<? extends DoubleSupplier> pref, float value) {
		applyFloatPref(true, pref, value);
	}

	default void applyFloatPref(boolean removeDefault, Pref<? extends DoubleSupplier> pref, float value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setFloatPref(pref, value);
		}
	}

	default void applyStringPref(Pref<? extends Supplier<String>> pref, String value) {
		applyStringPref(true, pref, value);
	}

	default void applyStringPref(boolean removeDefault, Pref<? extends Supplier<String>> pref, String value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setStringPref(pref, value);
		}
	}

	default void applyStringArrayPref(Pref<? extends Supplier<String[]>> pref, String[] value) {
		applyStringArrayPref(true, pref, value);
	}

	default void applyStringArrayPref(boolean removeDefault, Pref<? extends Supplier<String[]>> pref, String[] value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setStringArrayPref(pref, value);
		}
	}

	default <T> void applyCompoundPref(Pref<? extends Compound<T>> pref, T value) {
		applyCompoundPref(true, pref, value);
	}

	default <T> void applyCompoundPref(boolean removeDefault, Pref<? extends Compound<T>> pref, T value) {
		try (Edit e = editPreferenceStore(removeDefault)) {
			e.setCompoundPref(pref, value);
		}
	}


	default void removePref(Pref<?> pref) {
		try (Edit e = editPreferenceStore()) {
			e.removePref(pref);
		}
	}

	interface Pref<S> {

		String getName();

		S getDefaultValue();

		default boolean isInheritable() {
			return true;
		}

		default PreferenceStore.Pref<S> withInheritance(boolean inheritable) {
			return new PrefBase<S>(getName(), getDefaultValue()) {
				@Override
				public boolean isInheritable() {
					return inheritable;
				}
			};
		}

		default PreferenceStore.Pref<S> withDefaultValue(S defaultValue) {
			return new PrefBase<>(getName(), defaultValue).withInheritance(isInheritable());
		}

		static Pref<BooleanSupplier> b(String name, boolean defaultValue) {
			return b(name, () -> defaultValue);
		}

		static Pref<BooleanSupplier> b(String name, BooleanSupplier defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<IntSupplier> i(String name, int defaultValue) {
			return i(name, () -> defaultValue);
		}

		static Pref<IntSupplier> i(String name, IntSupplier defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<Supplier<int[]>> ia(String name, int[] defaultValue) {
			return ia(name, () -> defaultValue);
		}

		static Pref<Supplier<int[]>> ia(String name, Supplier<int[]> defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<IntSupplier> ss(String name, short defaultValue) {
			return ss(name, () -> defaultValue);
		}

		static Pref<IntSupplier> ss(String name, IntSupplier defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<Supplier<short[]>> sha(String name, short[] defaultValue) {
			return sha(name, () -> defaultValue);
		}

		static Pref<Supplier<short[]>> sha(String name, Supplier<short[]> defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<LongSupplier> l(String name, long defaultValue) {
			return l(name, () -> defaultValue);
		}

		static Pref<LongSupplier> l(String name, LongSupplier defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<DoubleSupplier> f(String name, float defaultValue) {
			return f(name, () -> defaultValue);
		}

		static Pref<DoubleSupplier> f(String name, DoubleSupplier defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<Supplier<String>> s(String name) {
			return s(name, () -> null);
		}

		static Pref<Supplier<String>> s(String name, String defaultValue) {
			return s(name, () -> defaultValue);
		}

		static Pref<Supplier<String>> s(String name, Supplier<String> defaultValue) {
			return create(name, defaultValue);
		}

		static Pref<Supplier<String[]>> sa(String name) {
			return sa(name, () -> new String[0]);
		}

		static Pref<Supplier<String[]>> sa(String name, String[] defaultValue) {
			return sa(name, () -> defaultValue);
		}

		static Pref<Supplier<String[]>> sa(String name, Supplier<String[]> defaultValue) {
			return create(name, defaultValue);
		}

		static <S> Pref<S> create(String name, S defaultValue) {
			return new PrefBase<>(name, defaultValue);
		}
	}

	interface Edit extends AutoCloseable {

		void setBooleanPref(Pref<? extends BooleanSupplier> pref, boolean value);

		void setIntPref(Pref<? extends IntSupplier> pref, int value);

		void setIntArrayPref(Pref<? extends Supplier<int[]>> pref, int[] value);

		void setLongPref(Pref<? extends LongSupplier> pref, long value);

		void setFloatPref(Pref<? extends DoubleSupplier> pref, float value);

		void setStringPref(Pref<? extends Supplier<String>> pref, String value);

		void setStringArrayPref(Pref<? extends Supplier<String[]>> pref, String[] value);

		default <T> void setCompoundPref(Pref<? extends Compound<T>> pref, T value) {
			pref.getDefaultValue().set(this, value);
		}

		boolean isRemoveDefault();

		void removePref(Pref<?> pref);

		void apply();

		@Override
		default void close() {
			apply();
		}
	}

	interface Compound<T> {

		T get(PreferenceStore store);

		void set(PreferenceStore.Edit edit, T value);
	}

	interface Listener {
		void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs);
	}
}
