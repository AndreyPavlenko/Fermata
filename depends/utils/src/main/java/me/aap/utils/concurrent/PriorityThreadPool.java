package me.aap.utils.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.function.CheckedSupplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Andrey Pavlenko
 */
public class PriorityThreadPool extends ThreadPool {
	public static final byte MIN_PRIORITY = Thread.MIN_PRIORITY;
	public static final byte NORM_PRIORITY = Thread.NORM_PRIORITY;
	public static final byte MAX_PRIORITY = Thread.MAX_PRIORITY;

	public PriorityThreadPool(int corePoolSize) {
		this(corePoolSize, corePoolSize, 60, SECONDS);
	}

	public PriorityThreadPool(int corePoolSize, int maximumPoolSize) {
		this(corePoolSize, maximumPoolSize, 60, SECONDS);
	}

	public PriorityThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new AbortPolicy());
	}

	public PriorityThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new PriorityBlockingQueue<>(), handler);
	}

	public FutureSupplier<?> submit(byte priority, Runnable task) {
		PriorityFuture<Void> t = PriorityFuture.create(requireNonNull(task), null, priority);
		execute(t);
		return t;
	}

	public <T> FutureSupplier<T> submit(byte priority, Callable<T> task) {
		PriorityFuture<T> t = PriorityFuture.create(requireNonNull(task), priority);
		execute(t);
		return t;
	}

	public <T> FutureSupplier<T> submit(byte priority, Runnable task, T result) {
		PriorityFuture<T> t = PriorityFuture.create(requireNonNull(task), result, priority);
		execute(t);
		return t;
	}

	@Override
	protected <T> Task<T> newTaskFor(Callable<T> callable) {
		return PriorityFuture.create(callable, NORM_PRIORITY);
	}

	@Override
	protected <T> Task<T> newTaskFor(Runnable runnable, T value) {
		return PriorityFuture.create(runnable, value, NORM_PRIORITY);
	}

	@Override
	protected <T> Task<T> newTaskFor(CheckedSupplier<T, Throwable> supplier) {
		return PriorityFuture.create(supplier, NORM_PRIORITY);
	}

	@Override
	protected <T> Task<T> newTaskFor(CheckedRunnable<Throwable> runnable, T value) {
		return PriorityFuture.create(runnable, value, NORM_PRIORITY);
	}

	private static abstract class PriorityFuture<T> extends Task<T> implements Comparable<PriorityFuture<T>> {
		final long time = System.currentTimeMillis();
		final byte priority;

		PriorityFuture(byte priority) {
			this.priority = priority;
		}

		static <V> PriorityFuture<V> create(Callable<V> callable, byte priority) {
			return new PriorityFuture<V>(priority) {
				@Override
				protected V runTask() throws Exception {
					return callable.call();
				}
			};
		}

		static <V> PriorityFuture<V> create(Runnable runnable, V value, byte priority) {
			return new PriorityFuture<V>(priority) {
				@Override
				protected V runTask() {
					runnable.run();
					return value;
				}
			};
		}

		public static <V> PriorityFuture<V> create(CheckedSupplier<V, Throwable> supplier, byte priority) {
			return new PriorityFuture<V>(priority) {
				@Override
				protected V runTask() throws Throwable {
					return supplier.get();
				}
			};
		}

		static <V> PriorityFuture<V> create(CheckedRunnable<Throwable> runnable, V value, byte priority) {
			return new PriorityFuture<V>(priority) {
				@Override
				protected V runTask() throws Throwable {
					runnable.run();
					return value;
				}
			};
		}

		@Override
		public int compareTo(PriorityFuture<T> o) {
			if (priority == o.priority) return Long.compare(time, o.time);
			else if (priority < o.priority) return 1;
			else return -1;
		}
	}
}
