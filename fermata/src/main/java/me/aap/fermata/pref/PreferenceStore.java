package me.aap.fermata.pref;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import me.aap.fermata.function.BooleanSupplier;
import me.aap.fermata.function.DoubleSupplier;
import me.aap.fermata.function.IntSupplier;
import me.aap.fermata.function.LongSupplier;
import me.aap.fermata.function.Supplier;

import me.aap.fermata.util.EventBroadcaster;

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

	boolean hasPref(Pref<?> pref);

	Edit editPreferenceStore();

	@NonNull
	default PreferenceStore getRootPreferenceStore() {
		return this;
	}

	@Nullable
	default PreferenceStore getParentPreferenceStore() {
		return null;
	}

	default void applyBooleanPref(Pref<? extends BooleanSupplier> pref, boolean value) {
		try (Edit e = editPreferenceStore()) {
			e.setBooleanPref(pref, value);
		}
	}

	default void applyIntPref(Pref<? extends IntSupplier> pref, int value) {
		try (Edit e = editPreferenceStore()) {
			e.setIntPref(pref, value);
		}
	}

	default void applyIntArrayPref(Pref<? extends Supplier<int[]>> pref, int[] value) {
		try (Edit e = editPreferenceStore()) {
			e.setIntArrayPref(pref, value);
		}
	}

	default void applyLongPref(Pref<? extends LongSupplier> pref, long value) {
		try (Edit e = editPreferenceStore()) {
			e.setLongPref(pref, value);
		}
	}

	default void applyFloatPref(Pref<? extends DoubleSupplier> pref, float value) {
		try (Edit e = editPreferenceStore()) {
			e.setFloatPref(pref, value);
		}
	}

	default void applyStringPref(Pref<? extends Supplier<String>> pref, String value) {
		try (Edit e = editPreferenceStore()) {
			e.setStringPref(pref, value);
		}
	}

	default void applyStringArrayPref(Pref<? extends Supplier<String[]>> pref, String[] value) {
		try (Edit e = editPreferenceStore()) {
			e.setStringArrayPref(pref, value);
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

		Pref<S> withInheritance(boolean inheritable);

		Pref<S> withDefaultValue(S defaultValue);

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

		static Pref<Supplier<String>> s(String name, String defaultValue) {
			return s(name, () -> defaultValue);
		}

		static Pref<Supplier<String>> s(String name, Supplier<String> defaultValue) {
			return create(name, defaultValue);
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

		void removePref(Pref<?> pref);

		void apply();

		@Override
		default void close() {
			apply();
		}
	}

	interface Listener {
		void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs);
	}
}
