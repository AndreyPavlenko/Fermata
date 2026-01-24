package me.aap.utils.holder;

import me.aap.utils.function.BiConsumer;

/**
 * @author Andrey Pavlenko
 */
public class BiHolder<T, U> implements BiConsumer<T, U> {
	public T value1;
	public U value2;

	public BiHolder() {
	}

	public BiHolder(T value1, U value2) {
		this.value1 = value1;
		this.value2 = value2;
	}

	public T getValue1() {
		return value1;
	}

	public U getValue2() {
		return value2;
	}

	public void setValue1(T value1) {
		this.value1 = value1;
	}

	public void setValue2(U value2) {
		this.value2 = value2;
	}

	@Override
	public void accept(T value1, U value2) {
		setValue1(value1);
		setValue2(value2);
	}

	@Override
	public String toString() {
		return "value1=" + getValue1() + ", value2=" + getValue2();
	}
}
