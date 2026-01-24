package me.aap.utils.function;

/**
 * @author Andrey Pavlenko
 */
public interface CheckedConsumer<T, E extends Throwable> {
	void accept(T t) throws E;
}
