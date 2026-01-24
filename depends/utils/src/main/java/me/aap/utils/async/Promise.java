package me.aap.utils.async;

/**
 * @author Andrey Pavlenko
 */
public class Promise<T> extends CompletableSupplier<T, T> {
	@Override
	protected T map(T value) {
		return value;
	}
}
