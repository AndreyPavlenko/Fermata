package me.aap.utils.function;

import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

/**
 * @author Andrey Pavlenko
 */
public interface ProgressiveResultConsumer<T> extends ResultConsumer<T> {
	int PROGRESS_UNKNOWN = -1;
	int PROGRESS_DONE = Integer.MAX_VALUE;

	static int progressShift(long total) {
		if (total <= Integer.MAX_VALUE) return 0;

		for (int shift = 1; ; shift++) {
			if ((total >> shift) <= Integer.MAX_VALUE) return shift;
		}
	}

	void accept(T result, Throwable fail, int progress, int total);

	@Override
	default void accept(T result, Throwable fail) {
		accept(result, fail, PROGRESS_DONE, PROGRESS_DONE);
	}

	interface Completion<T> extends ResultConsumer<T>, ProgressiveResultConsumer<T> {

		void onCompletion(T result, Throwable fail);

		@Override
		default void accept(T result, Throwable fail, int progress, int total) {
			if (progress == PROGRESS_DONE) onCompletion(result, fail);
		}
	}

	interface Success<T> extends ResultConsumer.Success<T>, ProgressiveResultConsumer<T> {

		@Override
		default void accept(T result, Throwable fail) {
			if (fail == null) accept(result);
		}

		@Override
		default void accept(T result, Throwable fail, int progress, int total) {
			if ((fail == null) && (progress == PROGRESS_DONE)) accept(result);
		}
	}

	interface Failure<T> extends ResultConsumer.Failure<T>, ProgressiveResultConsumer<T> {

		@Override
		default void accept(T result, Throwable fail) {
			if (fail != null) accept(fail);
		}

		@Override
		default void accept(T result, Throwable fail, int progress, int total) {
			if (fail != null) accept(fail);
		}
	}

	interface Cancel<T> extends ResultConsumer.Cancel<T>, ProgressiveResultConsumer<T> {

		@Override
		default void accept(T result, Throwable fail) {
			if (isCancellation(fail)) run();
		}

		@Override
		default void accept(T result, Throwable fail, int progress, int total) {
			if (isCancellation(fail)) run();
		}
	}

	interface Progress<T> extends ProgressiveResultConsumer<T> {

		void onProgress(T incomplete, int progress, int total);

		@Override
		default void accept(T result, Throwable fail, int progress, int total) {
			if ((fail == null) && (progress != PROGRESS_DONE)) onProgress(result, progress, total);
		}
	}
}