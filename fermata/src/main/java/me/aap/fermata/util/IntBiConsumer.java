package me.aap.fermata.util;

/**
 * @author Andrey Pavlenko
 */
public interface IntBiConsumer<T, U> {
	void accept(int i, T t, U u);
}
