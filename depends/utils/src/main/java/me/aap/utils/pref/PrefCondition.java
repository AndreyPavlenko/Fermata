package me.aap.utils.pref;

import androidx.annotation.Nullable;

import java.util.List;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Predicate;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.misc.ChangeableCondition;

/**
 * @author Andrey Pavlenko
 */
public class PrefCondition<T> implements ChangeableCondition, PreferenceStore.Listener {
	private final PreferenceStore store;
	private final Pref<T> pref;
	private final Predicate<Pref<T>> predicate;
	private Listener listener;

	public PrefCondition(PreferenceStore store, Pref<T> pref,
											 Predicate<Pref<T>> predicate) {
		this.store = store;
		this.pref = pref;
		this.predicate = predicate;
	}

	public static PrefCondition<BooleanSupplier> create(PreferenceStore store,
																											Pref<BooleanSupplier> pref) {
		return new PrefCondition<>(store, pref, store::getBooleanPref);
	}

	@Override
	public boolean get() {
		return predicate.test(pref);
	}

	@Override
	public void setListener(@Nullable Listener listener) {
		if (listener != null) {
			this.listener = listener;
			store.addBroadcastListener(this);
		} else {
			this.listener = null;
			store.removeBroadcastListener(this);
		}
	}

	@Override
	public ChangeableCondition copy() {
		return new PrefCondition<T>(store, pref, predicate);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if ((listener != null) && prefs.contains(pref)) listener.onConditionChanged(this);
	}
}
