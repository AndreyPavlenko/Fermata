package me.aap.utils.holder;

import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.function.BooleanSupplier;

/**
 * @author Andrey Pavlenko
 */
public class BooleanHolder implements BooleanConsumer, BooleanSupplier {
	public boolean value;

	public BooleanHolder() {
	}

	public BooleanHolder(boolean value) {
		this.value = value;
	}

	public boolean get() {
		return value;
	}

	@Override
	public boolean getAsBoolean() {
		return get();
	}

	public void set(boolean value) {
		this.value = value;
	}

	@Override
	public void accept(boolean t) {
		set(t);
	}

	@Override
	public String toString() {
		return String.valueOf(get());
	}
}
