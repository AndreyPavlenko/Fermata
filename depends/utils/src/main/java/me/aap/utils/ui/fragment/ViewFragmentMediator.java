package me.aap.utils.ui.fragment;

import android.view.View;

import androidx.annotation.Nullable;

import me.aap.utils.function.Consumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.ActivityListener;

import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_DESTROY;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public interface ViewFragmentMediator<V extends View> {
	byte DEFAULT_EVENT_MASK = ACTIVITY_DESTROY | FRAGMENT_CHANGED | FRAGMENT_CONTENT_CHANGED;

	void enable(V view, ActivityFragment f);

	default void disable(V view) {
	}

	default void onActivityEvent(V view, ActivityDelegate a, long e) {
	}

	default long getActivityEventMask() {
		return DEFAULT_EVENT_MASK;
	}

	static <V extends View & ActivityListener, M extends ViewFragmentMediator<V>>
	boolean attachMediator(V v, @Nullable ActivityFragment f, Supplier<M> getNew,
												 Supplier<M> getCurrent, Consumer<M> setCurrent) {
		M current = getCurrent.get();

		if (f == null) {
			if (current != null) {
				current.disable(v);
				setCurrent.accept(null);
				return true;
			} else {
				return false;
			}
		}

		M m = getNew.get();
		if (current == m) return false;

		setCurrent.accept(m);
		if (current != null) current.disable(v);
		if (m != null) m.enable(v, f);
		return true;
	}

	class JointMediator<V extends View, M extends ViewFragmentMediator<V>>
			implements ViewFragmentMediator<V> {
		protected final M m1;
		protected final M m2;

		public JointMediator(M m1, M m2) {
			this.m1 = m1;
			this.m2 = m2;
		}

		@Override
		public void enable(V v, ActivityFragment f) {
			m1.enable(v, f);
			m2.enable(v, f);
		}

		@Override
		public void disable(V v) {
			m1.disable(v);
			m2.disable(v);
		}

		@Override
		public void onActivityEvent(V v, ActivityDelegate a, long e) {
			m1.onActivityEvent(v, a, e);
			m2.onActivityEvent(v, a, e);
		}

		@Override
		public long getActivityEventMask() {
			return m1.getActivityEventMask() | m2.getActivityEventMask();
		}
	}
}
