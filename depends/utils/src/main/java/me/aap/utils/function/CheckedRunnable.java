package me.aap.utils.function;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public interface CheckedRunnable<T extends Throwable> {

	void run() throws T;

	static <T extends Throwable> void runWithRetry(@NonNull CheckedRunnable run) {
		runWithRetry(run, null, null);
	}

	static <T extends Throwable> void runWithRetry(@NonNull CheckedRunnable run,
																								 @Nullable Consumer<Throwable> onFailure) {
		runWithRetry(run, onFailure, null);
	}

	static <T extends Throwable> void runWithRetry(@NonNull CheckedRunnable run,
																								 @Nullable Consumer<Throwable> onFailure,
																								 @Nullable String msg) {
		try {
			run.run();
		} catch (Throwable ex) {
			Log.d(ex, ((msg != null) ? msg : ex.getMessage()) + ". Retrying...");

			try {
				run.run();
			} catch (Throwable ex1) {
				if (onFailure != null) {
					onFailure.accept(ex1);
				} else {
					Log.e(ex1, (msg != null) ? msg : ex.getMessage());
				}
			}
		}
	}
}
