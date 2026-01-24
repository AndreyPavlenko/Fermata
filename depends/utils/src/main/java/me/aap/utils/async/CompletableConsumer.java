package me.aap.utils.async;

import me.aap.utils.function.ProgressiveResultConsumer;

import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

/**
 * @author Andrey Pavlenko
 */
public interface CompletableConsumer<C> extends Completable<C>, ProgressiveResultConsumer<C> {

	@Override
	default void accept(C result, Throwable fail, int progress, int total) {
		if (fail != null) {
			if (isCancellation(fail)) cancel();
			else completeExceptionally(fail);
		} else if (progress == PROGRESS_DONE) {
			complete(result);
		} else {
			setProgress(result, progress, total);
		}
	}
}
