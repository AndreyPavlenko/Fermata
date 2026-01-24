package me.aap.utils.function;

/**
 * @author Andrey Pavlenko
 */
public interface IntObjectFunction<T, R> {
	R apply(int i, T t);
}
