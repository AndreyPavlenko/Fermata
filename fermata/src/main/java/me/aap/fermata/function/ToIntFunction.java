package me.aap.fermata.function;

/**
 * @author Andrey Pavlenko
 */
public interface ToIntFunction<T> {
	int applyAsInt(T value);
}
