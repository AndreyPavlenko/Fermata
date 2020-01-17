package me.aap.fermata.util;

import androidx.annotation.NonNull;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Pavlenko
 */
public class CompletedFuture<V> implements Future<V> {
	private final V result;

	public CompletedFuture(V result) {
		this.result = result;
	}

	@SuppressWarnings("unchecked")
	public static <V> CompletedFuture<V> nullResult() {
		return NullResultFutureHolder.instance;
	}

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
		return true;
	}

	@Override
	public V get() {
		return result;
	}

	@Override
	public V get(long timeout, @NonNull TimeUnit unit) {
		return result;
	}

	@SuppressWarnings("unchecked")
	private interface NullResultFutureHolder {
		CompletedFuture instance = new CompletedFuture(null);
	}
}
