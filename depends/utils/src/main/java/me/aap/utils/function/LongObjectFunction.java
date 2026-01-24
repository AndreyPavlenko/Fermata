package me.aap.utils.function;

/**
 * @author Andrey Pavlenko
 */
public interface LongObjectFunction<T, R> {
	R apply(long i, T t);
}
