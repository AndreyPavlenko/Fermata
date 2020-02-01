package me.aap.fermata.util;

import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public interface ChangeableCondition {

	boolean get();

	void setListener(@Nullable Listener listener);

	interface Listener {
		void onConditionChanged(ChangeableCondition condition);
	}

	default ChangeableCondition and(ChangeableCondition condition) {
		class And implements ChangeableCondition, Listener {
			Listener listener;

			@Override
			public boolean get() {
				return ChangeableCondition.this.get() && condition.get();
			}

			@Override
			public void setListener(@Nullable Listener listener) {
				if (listener != null) {
					this.listener = listener;
					condition.setListener(this);
					ChangeableCondition.this.setListener(this);
				} else {
					condition.setListener(null);
					ChangeableCondition.this.setListener(null);
				}
			}

			@Override
			public void onConditionChanged(ChangeableCondition condition) {
				if (listener != null) listener.onConditionChanged(this);
			}
		}

		return new And();
	}

	default ChangeableCondition or(ChangeableCondition condition) {
		class Or implements ChangeableCondition, Listener {
			Listener listener;

			@Override
			public boolean get() {
				return ChangeableCondition.this.get() || condition.get();
			}

			@Override
			public void setListener(@Nullable Listener listener) {
				if (listener != null) {
					this.listener = listener;
					condition.setListener(this);
					ChangeableCondition.this.setListener(this);
				} else {
					condition.setListener(null);
					ChangeableCondition.this.setListener(null);
				}
			}

			@Override
			public void onConditionChanged(ChangeableCondition condition) {
				if (listener != null) listener.onConditionChanged(this);
			}
		}

		return new Or();
	}
}
