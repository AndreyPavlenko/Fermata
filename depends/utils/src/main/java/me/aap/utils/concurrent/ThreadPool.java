package me.aap.utils.concurrent;

import androidx.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.RunnablePromise;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.log.Log;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Andrey Pavlenko
 */
public class ThreadPool extends ThreadPoolExecutor implements ThreadFactory, Thread.UncaughtExceptionHandler {
	final AtomicInteger counter = new AtomicInteger();

	public ThreadPool(int corePoolSize) {
		this(corePoolSize, corePoolSize);
	}

	public ThreadPool(int corePoolSize, int maximumPoolSize) {
		this(corePoolSize, maximumPoolSize, 60, SECONDS);
	}

	public ThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>());
	}

	public ThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
										BlockingQueue<Runnable> workQueue) {
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new DefaultRejectPolicy());
	}

	public ThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
										BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
		setThreadFactory(this);
		allowCoreThreadTimeOut(true);
	}

	@NonNull
	@Override
	public FutureSupplier<?> submit(Runnable task) {
		return submit(task, null);
	}

	@NonNull
	@Override
	public <T> FutureSupplier<T> submit(Runnable task, T result) {
		RunnablePromise<T> t = newTaskFor(requireNonNull(task), result);
		execute(t);
		return t;
	}

	@NonNull
	@Override
	public <T> FutureSupplier<T> submit(Callable<T> task) {
		RunnablePromise<T> t = newTaskFor(requireNonNull(task));
		execute(t);
		return t;
	}

	@NonNull
	public FutureSupplier<?> submitTask(CheckedRunnable<Throwable> task) {
		return submitTask(task, null);
	}

	@NonNull
	public <T> FutureSupplier<T> submitTask(CheckedRunnable<Throwable> task, T result) {
		RunnablePromise<T> t = newTaskFor(requireNonNull(task), result);
		execute(t);
		return t;
	}

	@NonNull
	public <T> FutureSupplier<T> submitTask(CheckedSupplier<T, Throwable> task) {
		RunnablePromise<T> t = newTaskFor(requireNonNull(task));
		execute(t);
		return t;
	}

	@Override
	protected <T> Task<T> newTaskFor(Callable<T> callable) {
		return Task.create(callable);
	}

	@Override
	protected <T> Task<T> newTaskFor(Runnable runnable, T value) {
		return Task.create(runnable, value);
	}

	protected <T> Task<T> newTaskFor(CheckedSupplier<T, Throwable> supplier) {
		return Task.create(supplier);
	}

	protected <T> Task<T> newTaskFor(CheckedRunnable<Throwable> runnable, T value) {
		return Task.create(runnable, value);
	}


	@Override
	public Thread newThread(@NonNull Runnable r) {
		PooledThread t = new PooledThread(r, "PooledThread-" + counter.incrementAndGet());
		t.setUncaughtExceptionHandler(this);
		return t;
	}

	@Override
	public void uncaughtException(@NonNull Thread t, @NonNull Throwable ex) {
		Log.e(ex, "Uncaught exception in thread ", t);
	}

	static abstract class Task<T> extends RunnablePromise<T> {

		static <V> Task<V> create(Callable<V> callable) {
			return new Task<V>() {
				@Override
				protected V runTask() throws Exception {
					return callable.call();
				}
			};
		}

		static <V> Task<V> create(Runnable runnable, V value) {
			return new Task<V>() {
				@Override
				protected V runTask() {
					runnable.run();
					return value;
				}
			};
		}

		public static <V> Task<V> create(CheckedSupplier<V, Throwable> supplier) {
			return new Task<V>() {
				@Override
				protected V runTask() throws Throwable {
					return supplier.get();
				}
			};
		}

		static <V> Task<V> create(CheckedRunnable<Throwable> runnable, V value) {
			return new Task<V>() {
				@Override
				protected V runTask() throws Throwable {
					runnable.run();
					return value;
				}
			};
		}
	}

	private static final class DefaultRejectPolicy implements RejectedExecutionHandler {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			if (!e.isShutdown()) {
				Log.w(getClass().getName(), "Task rejected, running in the same thread");
				r.run();
			}
		}
	}
}
