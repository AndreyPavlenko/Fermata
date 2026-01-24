package me.aap.utils.function;

import java.util.Objects;

/**
 * @author Andrey Pavlenko
 */
public interface BiFunction<T, U, R> {

	R apply(T t, U u);

	default <V> java.util.function.BiFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
		Objects.requireNonNull(after);
		return (T t, U u) -> after.apply(apply(t, u));
	}
}
