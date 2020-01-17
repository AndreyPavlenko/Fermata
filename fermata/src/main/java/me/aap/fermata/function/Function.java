package me.aap.fermata.function;

/**
 * @author Andrey Pavlenko
 */
public interface Function<T, R> {
	R apply(T t);
}
