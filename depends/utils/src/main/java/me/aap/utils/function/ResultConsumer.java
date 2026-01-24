package me.aap.utils.function;

import java.util.concurrent.CancellationException;

/**
 * @author Andrey Pavlenko
 */
public interface ResultConsumer<T> extends BiConsumer<T, Throwable> {

	interface Success<T> extends Consumer<T>, ResultConsumer<T> {
		@Override
		default void accept(T result, Throwable fail) {
			if (fail == null) accept(result);
		}
	}

	interface Failure<T> extends Consumer<Throwable>, ResultConsumer<T> {
		@Override
		default void accept(T result, Throwable fail) {
			if (fail != null) accept(fail);
		}
	}

	interface Cancel<T> extends Runnable, ResultConsumer<T> {

		static boolean isCancellation(Throwable fail) {
			return (fail instanceof CancellationException);
		}

		static Throwable newCancellation() {
			return new CancellationException();
		}

		@Override
		default void accept(T result, Throwable fail) {
			if (isCancellation(fail)) run();
		}
	}
}