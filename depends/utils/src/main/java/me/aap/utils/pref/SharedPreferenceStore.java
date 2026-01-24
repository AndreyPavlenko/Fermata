package me.aap.utils.pref;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

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

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntBiConsumer;
import me.aap.utils.function.IntFunction;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;

/**
 * @author Andrey Pavlenko
 */
public interface SharedPreferenceStore extends PreferenceStore {

	@NonNull
	SharedPreferences getSharedPreferences();

	static SharedPreferenceStore create(Context ctx, String name) {
		return create(ctx.getSharedPreferences(name,Context.MODE_PRIVATE));
	}

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

	default <S> Pref<S> getPref(Pref<S> pref) {
		return pref;
	}

	default String getPreferenceKey(Pref<?> pref) {
		return pref.getName();
	}

	@Override
	default boolean getBooleanPref(Pref<? extends BooleanSupplier> pref) {
		pref = getPref(pref);
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
		pref = getPref(pref);
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
		pref = getPref(pref);
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
		pref = getPref(pref);
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
		pref = getPref(pref);
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
		pref = getPref(pref);
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
					Log.e(ex, "Preference key ", pref.getName(), " has invalid value: ", s);
				}
			}

			return v;
		}
	}

	default <A> A getArrayPref(Pref<? extends Supplier<A>> pref, IntFunction<A> newFunc,
														 IntBiConsumer<A, String> add) {
		pref = getPref(pref);
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
		pref = getPref(pref);

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
	default PreferenceStore.Edit editPreferenceStore(boolean removeDefault) {
		return new Edit(removeDefault, this);
	}

	default void notifyPreferenceChange(PreferenceStore store, List<Pref<?>> prefs) {
		fireBroadcastEvent(l -> l.onPreferenceChanged(store, prefs));
	}

	class Edit implements PreferenceStore.Edit {
		private final boolean removeDefault;
		private final SharedPreferenceStore store;
		private final SharedPreferences.Editor edit;
		private List<Pref<?>> changed;

		@SuppressLint("CommitPrefEdits")
		Edit(boolean removeDefault, SharedPreferenceStore store) {
			this.removeDefault = removeDefault;
			this.store = store;
			this.edit = store.getSharedPreferences().edit();
		}

		@Override
		public void setBooleanPref(Pref<? extends BooleanSupplier> pref, boolean value) {
			Pref<? extends BooleanSupplier> p = store.getPref(pref);

			if ((p.getDefaultValue().getAsBoolean() != value) ||
					!removeDefault(p, s -> s.getBooleanPref(p) == value)) {
				String key = store.getPreferenceKey(p);
				edit.putBoolean(key, value);
				notifyPreferenceChange(p);
			}
		}

		@Override
		public void setIntPref(Pref<? extends IntSupplier> pref, int value) {
			Pref<? extends IntSupplier> p = store.getPref(pref);

			if ((p.getDefaultValue().getAsInt() != value) ||
					!removeDefault(p, s -> s.getIntPref(p) == value)) {
				String key = store.getPreferenceKey(p);
				edit.putInt(key, value);
				notifyPreferenceChange(p);
			}
		}

		@Override
		public void setIntArrayPref(Pref<? extends Supplier<int[]>> pref, int[] value) {
			Pref<? extends Supplier<int[]>> p = store.getPref(pref);

			if (!Arrays.equals(p.getDefaultValue().get(), value) ||
					!removeDefault(p, s -> Arrays.equals(s.getIntArrayPref(p), value))) {
				setArrayPref(p, value, value.length, (i, a, sb) -> sb.append(a[i]));
			}
		}

		@Override
		public void setLongPref(Pref<? extends LongSupplier> pref, long value) {
			Pref<? extends LongSupplier> p = store.getPref(pref);

			if ((p.getDefaultValue().getAsLong() != value) ||
					!removeDefault(p, s -> s.getLongPref(p) == value)) {
				String key = store.getPreferenceKey(p);
				edit.putLong(key, value);
				notifyPreferenceChange(p);
			}
		}

		@Override
		public void setFloatPref(Pref<? extends DoubleSupplier> pref, float value) {
			Pref<? extends DoubleSupplier> p = store.getPref(pref);

			if ((p.getDefaultValue().getAsDouble() != value) ||
					!removeDefault(p, s -> s.getFloatPref(p) == value)) {
				String key = store.getPreferenceKey(p);
				edit.putFloat(key, value);
				notifyPreferenceChange(p);
			}
		}

		@Override
		public void setStringPref(Pref<? extends Supplier<String>> pref, String value) {
			Pref<? extends Supplier<String>> p = store.getPref(pref);

			if (!Objects.equals(p.getDefaultValue().get(), value) ||
					!removeDefault(p, s -> Objects.equals(s.getStringPref(p), value))) {
				String key = store.getPreferenceKey(p);
				edit.putString(key, value);
				notifyPreferenceChange(p);
			}
		}

		@Override
		public void setStringArrayPref(Pref<? extends Supplier<String[]>> pref, String[] value) {
			Pref<? extends Supplier<String[]>> p = store.getPref(pref);

			if (!Arrays.equals(p.getDefaultValue().get(), value) ||
					!removeDefault(p, s -> Arrays.equals(s.getStringArrayPref(p), value))) {
				String key = store.getPreferenceKey(p);
				Set<String> set = new HashSet<>((int) (value.length * 1.5f));

				try (SharedTextBuilder tb = SharedTextBuilder.get()) {
					for (int i = 0; i < value.length; i++) {
						tb.setLength(0);
						tb.append(i).append(' ').append(value[i]);
						set.add(tb.toString());
					}
				}

				edit.putStringSet(key, set);
				notifyPreferenceChange(p);
			}
		}

		public <A> void setArrayPref(Pref<? extends Supplier<A>> pref, A value, int len,
																 IntBiConsumer<A, TextBuilder> appendFunc) {
			Pref<? extends Supplier<A>> p = store.getPref(pref);
			String key = store.getPreferenceKey(p);

			try (SharedTextBuilder sb = SharedTextBuilder.get()) {
				for (int i = 0; i < len; i++) {
					appendFunc.accept(i, value, sb);
					if (i != (len - 1)) sb.append(' ');
				}

				edit.putString(key, sb.toString());
			}

			notifyPreferenceChange(p);
		}

		@Override
		public void removePref(Pref<?> pref) {
			Pref<?> p = store.getPref(pref);
			edit.remove(store.getPreferenceKey(p));
			notifyPreferenceChange(p);
		}

		@Override
		public void apply() {
			if (changed != null) {
				edit.apply();
				store.notifyPreferenceChange(store, changed);
				changed = null;
			}
		}

		@Override
		public boolean isRemoveDefault() {
			return removeDefault;
		}

		private <S> boolean removeDefault(Pref<S> pref, Function<PreferenceStore, Boolean> compare) {
			if (!isRemoveDefault()) return false;

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
