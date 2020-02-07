package me.aap.fermata.pref;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import me.aap.fermata.function.BooleanSupplier;
import me.aap.fermata.function.DoubleSupplier;
import me.aap.fermata.function.Function;
import me.aap.fermata.function.IntBiConsumer;
import me.aap.fermata.function.IntFunction;
import me.aap.fermata.function.IntSupplier;
import me.aap.fermata.function.LongSupplier;
import me.aap.fermata.function.Supplier;
import me.aap.fermata.util.Utils;

/**
 * @author Andrey Pavlenko
 */
public interface SharedPreferenceStore extends PreferenceStore {

	@NonNull
	SharedPreferences getSharedPreferences();

	static SharedPreferenceStore create(SharedPreferences prefs) {
		return new SharedPreferenceStore() {
			private Collection<ListenerRef<Listener>> listeners;

			@NonNull
			@Override
			public SharedPreferences getSharedPreferences() {
				return prefs;
			}

			@Override
			public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
				return (listeners != null) ? listeners : (listeners = new LinkedList<>());
			}
		};
	}

	static SharedPreferenceStore create(SharedPreferences prefs, PreferenceStore parent) {
		return new SharedPreferenceStore() {

			@NonNull
			@Override
			public SharedPreferences getSharedPreferences() {
				return prefs;
			}

			@Override
			public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
				return parent.getBroadcastEventListeners();
			}
		};
	}

	default String getPreferenceKey(Pref<?> pref) {
		return pref.getName();
	}

	@Override
	default boolean getBooleanPref(Pref<? extends BooleanSupplier> pref) {
		SharedPreferences prefs = getSharedPreferences();
		String k = getPreferenceKey(pref);

		if (!prefs.contains(k)) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent == null) ? pref.getDefaultValue().getAsBoolean() : parent.getBooleanPref(pref);
			} else {
				return pref.getDefaultValue().getAsBoolean();
			}
		} else {
			return prefs.getBoolean(k, false);
		}
	}

	@Override
	default int getIntPref(Pref<? extends IntSupplier> pref) {
		SharedPreferences prefs = getSharedPreferences();
		String k = getPreferenceKey(pref);

		if (!prefs.contains(k)) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent == null) ? pref.getDefaultValue().getAsInt() : parent.getIntPref(pref);
			} else {
				return pref.getDefaultValue().getAsInt();
			}
		} else {
			return prefs.getInt(k, 0);
		}
	}

	@Override
	default int[] getIntArrayPref(Pref<? extends Supplier<int[]>> pref) {
		return getArrayPref(pref, int[]::new, (i, a, v) -> a[i] = Integer.parseInt(v));
	}

	@Override
	default long getLongPref(Pref<? extends LongSupplier> pref) {
		SharedPreferences prefs = getSharedPreferences();
		String k = getPreferenceKey(pref);

		if (!prefs.contains(k)) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent == null) ? pref.getDefaultValue().getAsLong() : parent.getLongPref(pref);
			} else {
				return pref.getDefaultValue().getAsLong();
			}
		} else {
			return prefs.getLong(k, 0);
		}
	}

	default long[] getLongArrayPref(Pref<? extends Supplier<long[]>> pref) {
		return getArrayPref(pref, long[]::new, (i, a, v) -> a[i] = Long.parseLong(v));
	}

	@Override
	default float getFloatPref(Pref<? extends DoubleSupplier> pref) {
		SharedPreferences prefs = getSharedPreferences();
		String k = getPreferenceKey(pref);

		if (!prefs.contains(k)) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent == null) ? (float) pref.getDefaultValue().getAsDouble() : parent.getFloatPref(pref);
			} else {
				return (float) pref.getDefaultValue().getAsDouble();
			}
		} else {
			return prefs.getFloat(k, 0);
		}
	}

	@Override
	default String getStringPref(Pref<? extends Supplier<String>> pref) {
		SharedPreferences prefs = getSharedPreferences();
		String v = prefs.getString(getPreferenceKey(pref), null);

		if (v == null) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent == null) ? pref.getDefaultValue().get() : parent.getStringPref(pref);
			} else {
				return pref.getDefaultValue().get();
			}
		} else {
			return v;
		}
	}

	default String[] getStringArrayPref(Pref<? extends Supplier<String[]>> pref) {
		SharedPreferences prefs = getSharedPreferences();
		Set<String> value = prefs.getStringSet(getPreferenceKey(pref), null);

		if (value == null) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent == null) ? pref.getDefaultValue().get() : parent.getStringArrayPref(pref);
			} else {
				return pref.getDefaultValue().get();
			}
		} else {
			String[] v = new String[value.size()];

			for (String s : value) {
				try {
					int i = s.indexOf(' ');
					int n = Integer.parseInt(s.substring(0, i));
					v[n] = s.substring(i + 1);
				} catch (Exception ex) {
					Log.e(getClass().getName(), "Preference key " + pref.getName() +
							" has invalid value: " + s, ex);
				}
			}

			return v;
		}
	}

	default <A> A getArrayPref(Pref<? extends Supplier<A>> pref, IntFunction<A> newFunc,
														 IntBiConsumer<A, String> add) {
		SharedPreferences prefs = getSharedPreferences();
		String value = prefs.getString(getPreferenceKey(pref), null);

		if ((value == null) || value.isEmpty()) {
			if (pref.isInheritable()) {
				PreferenceStore parent = getParentPreferenceStore();
				return (parent instanceof SharedPreferenceStore)
						? ((SharedPreferenceStore) parent).getArrayPref(pref, newFunc, add)
						: pref.getDefaultValue().get();
			} else {
				return pref.getDefaultValue().get();
			}
		} else {
			String[] v = value.split(" ");
			A a = newFunc.apply(v.length);
			for (int i = 0; i < v.length; i++) {
				add.accept(i, a, v[i]);
			}
			return a;
		}
	}

	@Override
	default boolean hasPref(Pref<?> pref, boolean checkParent) {
		SharedPreferences prefs = getSharedPreferences();

		if (prefs.contains(getPreferenceKey(pref))) {
			return true;
		} else if (checkParent && pref.isInheritable()) {
			PreferenceStore parent = getParentPreferenceStore();
			return (parent != null) && parent.hasPref(pref, true);
		} else {
			return false;
		}
	}

	@Override
	default PreferenceStore.Edit editPreferenceStore() {
		return new Edit(this);
	}

	default void notifyPreferenceChange(PreferenceStore store, List<Pref<?>> prefs) {
		fireBroadcastEvent(l -> l.onPreferenceChanged(store, prefs));
	}

	class Edit implements PreferenceStore.Edit {
		private final SharedPreferenceStore store;
		private final SharedPreferences.Editor edit;
		private List<Pref<?>> changed;

		@SuppressLint("CommitPrefEdits")
		Edit(SharedPreferenceStore store) {
			this.store = store;
			this.edit = store.getSharedPreferences().edit();
		}

		@Override
		public void setBooleanPref(Pref<? extends BooleanSupplier> pref, boolean value) {
			if ((pref.getDefaultValue().getAsBoolean() != value) ||
					!removeDefault(pref, p -> p.getBooleanPref(pref) == value)) {
				String key = store.getPreferenceKey(pref);
				edit.putBoolean(key, value);
				notifyPreferenceChange(pref);
			}
		}

		@Override
		public void setIntPref(Pref<? extends IntSupplier> pref, int value) {
			if ((pref.getDefaultValue().getAsInt() != value) ||
					!removeDefault(pref, p -> p.getIntPref(pref) == value)) {
				String key = store.getPreferenceKey(pref);
				edit.putInt(key, value);
				notifyPreferenceChange(pref);
			}
		}

		@Override
		public void setIntArrayPref(Pref<? extends Supplier<int[]>> pref, int[] value) {
			if (!Arrays.equals(pref.getDefaultValue().get(), value) ||
					!removeDefault(pref, p -> Arrays.equals(p.getIntArrayPref(pref), value))) {
				setArrayPref(pref, value, value.length, (i, a, sb) -> sb.append(a[i]));
			}
		}

		@Override
		public void setLongPref(Pref<? extends LongSupplier> pref, long value) {
			if ((pref.getDefaultValue().getAsLong() != value) ||
					!removeDefault(pref, p -> p.getLongPref(pref) == value)) {
				String key = store.getPreferenceKey(pref);
				edit.putLong(key, value);
				notifyPreferenceChange(pref);
			}
		}

		@Override
		public void setFloatPref(Pref<? extends DoubleSupplier> pref, float value) {
			if ((pref.getDefaultValue().getAsDouble() != value) ||
					!removeDefault(pref, p -> p.getFloatPref(pref) == value)) {
				String key = store.getPreferenceKey(pref);
				edit.putFloat(key, value);
				notifyPreferenceChange(pref);
			}
		}

		@Override
		public void setStringPref(Pref<? extends Supplier<String>> pref, String value) {
			if (!Objects.equals(pref.getDefaultValue().get(), value) ||
					!removeDefault(pref, p -> Objects.equals(p.getStringPref(pref), value))) {
				String key = store.getPreferenceKey(pref);
				edit.putString(key, value);
				notifyPreferenceChange(pref);
			}
		}

		@Override
		public void setStringArrayPref(Pref<? extends Supplier<String[]>> pref, String[] value) {
			if (!Arrays.equals(pref.getDefaultValue().get(), value) ||
					!removeDefault(pref, p -> Arrays.equals(p.getStringArrayPref(pref), value))) {
				String key = store.getPreferenceKey(pref);
				Set<String> set = new HashSet<>((int) (value.length * 1.5f));
				StringBuilder sb = Utils.getSharedStringBuilder();

				for (int i = 0; i < value.length; i++) {
					sb.setLength(0);
					sb.append(i).append(' ').append(value[i]);
					set.add(sb.toString());
				}

				edit.putStringSet(key, set);
				notifyPreferenceChange(pref);
			}
		}

		public <A> void setArrayPref(Pref<? extends Supplier<A>> pref, A value, int len,
																 IntBiConsumer<A, StringBuilder> appendFunc) {
			String key = store.getPreferenceKey(pref);
			StringBuilder sb = Utils.getSharedStringBuilder();

			for (int i = 0; i < len; i++) {
				appendFunc.accept(i, value, sb);
				if (i != (len - 1)) sb.append(' ');
			}

			edit.putString(key, sb.toString());
			notifyPreferenceChange(pref);
		}

		@Override
		public void removePref(Pref<?> pref) {
			edit.remove(store.getPreferenceKey(pref));
			notifyPreferenceChange(pref);
		}

		@Override
		public void apply() {
			if (changed != null) {
				edit.apply();
				store.notifyPreferenceChange(store, changed);
				changed = null;
			}
		}

		private <S> boolean removeDefault(Pref<S> pref, Function<PreferenceStore, Boolean> compare) {
			if (!pref.isInheritable()) {
				removePref(pref);
				return true;
			}

			PreferenceStore parent = store.getParentPreferenceStore();

			if ((parent == null) || compare.apply(parent)) {
				removePref(pref);
				return true;
			}

			return false;
		}

		private void notifyPreferenceChange(Pref<?> pref) {
			if (changed == null) {
				changed = Collections.singletonList(pref);
			} else if (changed.size() == 1) {
				List<Pref<?>> l = new ArrayList<>();
				//noinspection CollectionAddAllCanBeReplacedWithConstructor
				l.addAll(changed);
				l.add(pref);
				changed = l;
			} else {
				changed.add(pref);
			}
		}
	}
}
