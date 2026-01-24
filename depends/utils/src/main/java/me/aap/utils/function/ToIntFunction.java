package me.aap.utils.function;

/**
 * @author Andrey Pavlenko
 */
public interface ToIntFunction<T> {
	int applyAsInt(T value);
}
