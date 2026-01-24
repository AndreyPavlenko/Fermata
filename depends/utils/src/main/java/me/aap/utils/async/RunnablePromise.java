package me.aap.utils.async;

import java.util.concurrent.RunnableFuture;

import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public abstract class RunnablePromise<T> extends Promise<T> implements RunnableFuture<T> {
	private volatile Thread thread;

	protected abstract T runTask() throws Throwable;

	@Override
	public final void run() {
		thread = Thread.currentThread();

		if (!isCancelled()) {
			try {
				complete(runTask());
			} catch (Throwable ex) {
				Log.d(ex, "Runnable promise failed");
				completeExceptionally(ex);
			}
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (!super.cancel(mayInterruptIfRunning)) return false;

		if (mayInterruptIfRunning) {
			Thread t = thread;
			if (t != null) t.interrupt();
		}

		return true;
	}

	public static <Void> RunnablePromise<Void> create(CheckedRunnable<Throwable> task) {
		return new RunnablePromise<Void>() {
			@Override
			protected Void runTask() throws Throwable {
				task.run();
				return null;
			}
		};
	}

	public static <R> RunnablePromise<R> create(CheckedSupplier<R, Throwable> task) {
		return new RunnablePromise<R>() {
			@Override
			protected R runTask() throws Throwable {
				return task.get();
			}
		};
	}

	public static <R, T> RunnablePromise<R> create(CheckedFunction<T, R, Throwable> task, T arg) {
		return new RunnablePromise<R>() {
			@Override
			protected R runTask() throws Throwable {
				return task.apply(arg);
			}
		};
	}
}
