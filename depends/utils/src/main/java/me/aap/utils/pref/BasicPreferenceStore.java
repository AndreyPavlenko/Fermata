package me.aap.utils.pref;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;

import me.aap.utils.event.BasicEventBroadcaster;

/**
 * @author Andrey Pavlenko
 */
public class BasicPreferenceStore extends BasicEventBroadcaster<PreferenceStore.Listener>
		implements PreferenceStore {
	private final Map<Pref<?>, Object> values = new HashMap<>();

	@Override
	public boolean getBooleanPref(Pref<? extends BooleanSupplier> pref) {
		Boolean value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().getAsBoolean();
	}

	@Override
	public int getIntPref(Pref<? extends IntSupplier> pref) {
		Integer value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().getAsInt();
	}

	@Override
	public int[] getIntArrayPref(Pref<? extends Supplier<int[]>> pref) {
		int[] value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().get();
	}

	@Override
	public long getLongPref(Pref<? extends LongSupplier> pref) {
		Long value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().getAsLong();
	}

	@Override
	public long[] getLongArrayPref(Pref<? extends Supplier<long[]>> pref) {
		long[] value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().get();
	}

	@Override
	public float getFloatPref(Pref<? extends DoubleSupplier> pref) {
		Float value = get(pref);
		return (value != null) ? value : (float) pref.getDefaultValue().getAsDouble();
	}

	@Override
	public String getStringPref(Pref<? extends Supplier<String>> pref) {
		String value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().get();
	}

	@Override
	public String[] getStringArrayPref(Pref<? extends Supplier<String[]>> pref) {
		String[] value = get(pref);
		return (value != null) ? value : pref.getDefaultValue().get();
	}

	@Override
	public boolean hasPref(Pref<?> pref, boolean checkParent) {
		return values.containsKey(pref);
	}

	@SuppressWarnings("unchecked")
	private <T> T get(Pref<?> key) {
		return (T) values.get(key);
	}

	@Override
	public Edit editPreferenceStore(boolean removeDefault) {
		return new Edit() {
			@Override
			public void setBooleanPref(Pref<? extends BooleanSupplier> pref, boolean value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void setIntPref(Pref<? extends IntSupplier> pref, int value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void setIntArrayPref(Pref<? extends Supplier<int[]>> pref, int[] value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void setLongPref(Pref<? extends LongSupplier> pref, long value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void setFloatPref(Pref<? extends DoubleSupplier> pref, float value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void setStringPref(Pref<? extends Supplier<String>> pref, String value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void setStringArrayPref(Pref<? extends Supplier<String[]>> pref, String[] value) {
				values.put(pref, value);
				notifyPreferenceChange(pref);
			}

			@Override
			public void removePref(Pref<?> pref) {
				values.remove(pref);
				notifyPreferenceChange(pref);
			}

			@Override
			public void apply() {
			}

			@Override
			public boolean isRemoveDefault() {
				return false;
			}

			private void notifyPreferenceChange(Pref<?> pref) {
				fireBroadcastEvent(l -> l.onPreferenceChanged(BasicPreferenceStore.this, Collections.singletonList(pref)));
			}
		};
	}
}
