package me.aap.fermata.function;

/**
 * @author Andrey Pavlenko
 */
public interface IntBiConsumer<T, U> {
	void accept(int i, T t, U u);
}
