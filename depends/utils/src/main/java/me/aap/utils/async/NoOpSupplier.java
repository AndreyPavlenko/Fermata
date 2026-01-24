package me.aap.utils.async;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import me.aap.utils.function.ProgressiveResultConsumer;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("rawtypes")
final class NoOpSupplier implements FutureSupplier {
	static final NoOpSupplier instance = new NoOpSupplier();

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public Object get() {
		return null;
	}

	@Override
	public Object get(long timeout, TimeUnit unit) {
		return null;
	}

	@Override
	public FutureSupplier addConsumer(@NonNull ProgressiveResultConsumer consumer) {
		return this;
	}

	@Nullable
	@Override
	public Throwable getFailure() {
		return null;
	}
}
