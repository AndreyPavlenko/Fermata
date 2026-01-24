package me.aap.utils.function;

/**
 * @author Andrey Pavlenko
 */
public interface CheckedSupplier<R, E extends Throwable> {
	R get() throws E;
}
