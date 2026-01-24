package me.aap.utils.async;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.utils.BuildConfig;
import me.aap.utils.app.App;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.CheckedBiConsumer;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.function.ProgressiveResultConsumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 * @noinspection unused, UnusedReturnValue
 */
public interface FutureSupplier<T> extends Future<T>, CheckedSupplier<T, Throwable>, Cancellable {

	FutureSupplier<T> addConsumer(@NonNull ProgressiveResultConsumer<? super T> consumer);

	default FutureSupplier<T> onCompletion(
			@NonNull ProgressiveResultConsumer.Completion<? super T> consumer) {
		return addConsumer(consumer);
	}

	default FutureSupplier<T> onSuccess(
			@NonNull ProgressiveResultConsumer.Success<? super T> consumer) {
		return addConsumer(consumer);
	}

	default FutureSupplier<T> onFailure(
			@NonNull ProgressiveResultConsumer.Failure<? super T> consumer) {
		return addConsumer(consumer);
	}

	default FutureSupplier<T> onCancel(
			@NonNull ProgressiveResultConsumer.Cancel<? super T> consumer) {
		return addConsumer(consumer);
	}

	default FutureSupplier<T> onProgress(
			@NonNull ProgressiveResultConsumer.Progress<? super T> consumer) {
		return addConsumer(consumer);
	}

	default FutureSupplier<T> onCompletionSupply(@NonNull Completable<? super T> consumer) {
		return onCompletion((r, err) -> {
			if (err != null) consumer.completeExceptionally(err);
			else consumer.complete(r);
		});
	}

	@Nullable
	Throwable getFailure();

	/**
	 * Returns true if completed exceptionally or cancelled
	 */
	default boolean isFailed() {
		return getFailure() != null;
	}

	default boolean isDoneNotFailed() {
		return isDone() && !isFailed();
	}

	@Nullable
	default Executor getExecutor() {
		return null;
	}

	default FutureSupplier<T> withExecutor(Executor executor) {
		return withExecutor(executor, true);
	}

	default FutureSupplier<T> withExecutor(Executor executor, boolean ignoreIfDone) {
		if ((executor == getExecutor()) || (ignoreIfDone && isDone())) return this;

		var p = new ProxySupplier<T, T>() {
			private final Executor exec;
			private volatile boolean cancelled;

			{
				if (executor instanceof HandlerExecutor handler) {
					exec = task -> {
						if (handler.getLooper().isCurrentThread()) {
							if (handler.isClosed()) Log.e("Handler closed! Unable to run task ", task);
							else task.run();
						} else {
							boolean canceled = isCancelled();
							handler.post(() -> {
								if (handler.isClosed()) Log.e("Handler closed! Unable to run task ", task);
								else if (canceled || !isCancelled()) task.run();
							});
						}
					};
				} else {
					exec = task -> {
						boolean canceled = isCancelled();
						executor.execute(() -> {
							if (canceled || !isCancelled()) task.run();
						});
					};
				}
			}

			@Nullable
			@Override
			public Executor getExecutor() {
				return exec;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				cancelled = true;
				if (!super.cancel(mayInterruptIfRunning)) return false;
				FutureSupplier.this.cancel(mayInterruptIfRunning);
				return true;
			}

			@Override
			public boolean isCancelled() {
				return cancelled;
			}

			@Override
			public boolean isFailed() {
				if (!super.isFailed()) return false;

				if (executor instanceof HandlerExecutor) {
					return ((HandlerExecutor) executor).getLooper().isCurrentThread();
				} else {
					return true;
				}
			}

			@Override
			public boolean isDone() {
				if (!super.isDone()) return false;

				if (executor instanceof HandlerExecutor) {
					return ((HandlerExecutor) executor).getLooper().isCurrentThread();
				} else {
					return true;
				}
			}

			@Override
			public T map(T t) {
				return t;
			}
		};

		addConsumer(p);
		return p;
	}

	/**
	 * @noinspection ConfusingMainMethod
	 */
	default FutureSupplier<T> main() {
		if (isDone() && isMainThread()) return this;
		return withExecutor(App.get().getHandler(), false);
	}

	/**
	 * @noinspection ConfusingMainMethod
	 */
	default FutureSupplier<T> main(HandlerExecutor handler) {
		if (isDone() && (Thread.currentThread() == handler.getLooper().getThread())) return this;
		return withExecutor(handler, false);
	}

	default FutureSupplier<T> fork() {
		if (isDone()) return this;
		var p = new Promise<T>();
		onCompletionSupply(p);
		return p;
	}

	default FutureSupplier<T> timeout(long millis) {
		return timeout(millis, () -> {
			throw new TimeoutException();
		});
	}

	default FutureSupplier<T> timeout(long millis, CheckedSupplier<T, Throwable> onTimeout) {
		if (isDone()) return this;
		var trace = BuildConfig.FUTURE_TRACE ? new TimeoutException() : null;
		var p = new Promise<T>();
		var t = App.get().getScheduler().schedule(() -> {
			if (p.isDone()) return;
			if (BuildConfig.FUTURE_TRACE) Log.d(trace, "FutureSupplier timed out");

			try {
				p.complete(onTimeout.get());
			} catch (Throwable ex) {
				p.completeExceptionally(ex);
			}
		}, millis, MILLISECONDS);

		onCompletion((r, err) -> {
			t.cancel(false);
			p.complete(r, err);
		});

		return p;
	}

	default T get(@Nullable Supplier<? extends T> onError) {
		try {
			return get();
		} catch (Throwable ex) {
			Log.e(ex);
			return (onError != null) ? onError.get() : null;
		}
	}

	default T get(@Nullable Supplier<? extends T> onError, long timeout, @NonNull TimeUnit unit) {
		try {
			return get(timeout, unit);
		} catch (Throwable ex) {
			Log.e(ex);
			return (onError != null) ? onError.get() : null;
		}
	}

	@Nullable
	default T peek() {
		return peek((Supplier<? extends T>) null);
	}

	default T peek(@Nullable T ifNotDone) {
		return peek(() -> ifNotDone);
	}

	default T peek(@Nullable Supplier<? extends T> ifNotDone) {
		if (isDone()) {
			if (!isFailed()) {
				try {
					return get();
				} catch (Throwable ex) {
					Log.e(ex);
				}
			} else {
				Log.e(getFailure());
			}
		}

		return (ifNotDone != null) ? ifNotDone.get() : null;
	}


	default T getOrThrow() throws RuntimeException {
		if (isDone()) {
			if (!isFailed()) {
				try {
					return get();
				} catch (Throwable ex) {
					throw new RuntimeException(ex);
				}
			} else {
				throw new RuntimeException(getFailure());
			}
		} else {
			throw new RuntimeException("FutureSupplier is not done");
		}
	}

	@Override
	default boolean cancel() {
		return cancel(true);
	}

	@SuppressWarnings("unchecked")
	default <R> FutureSupplier<R> map(CheckedFunction<? super T, ? extends R, Throwable> map) {
		if (isDone()) {
			if (isFailed()) return (FutureSupplier<R>) this;

			try {
				return completed(map.apply(get()));
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}

		return ProxySupplier.create(this, map, ex -> {
			if (isCancellation(ex)) FutureSupplier.this.cancel();
			throw ex;
		});
	}

	default <R> FutureSupplier<R> mapIfNotNull(
			CheckedFunction<? super T, ? extends R, Throwable> map) {
		return map(v -> (v != null) ? map.apply(v) : null);
	}

	@SuppressWarnings("unchecked")
	default <R> FutureSupplier<R> cast() {
		return (FutureSupplier<R>) this;
	}

	default FutureSupplier<T> ifFail(CheckedFunction<Throwable, ? extends T, Throwable> onFail) {
		if (isDone()) {
			if (isFailed()) {
				try {
					return completed(onFail.apply(getFailure()));
				} catch (Throwable ex) {
					return failed(ex);
				}
			} else {
				return this;
			}
		}

		return ProxySupplier.create(this, t -> t, onFail);
	}

	default FutureSupplier<T> ifNull(CheckedSupplier<T, Throwable> f) {
		if (isDone()) {
			if (!isFailed()) {
				T v = peek();
				if (v == null) {
					try {
						return completed(f.get());
					} catch (Throwable ex) {
						return failed(ex);
					}
				}
			}
			return this;
		} else {
			return then(v -> {
				if (v == null) {
					try {
						return completed(f.get());
					} catch (Throwable ex) {
						return failed(ex);
					}
				} else {
					return completed(v);
				}
			});
		}
	}

	default FutureSupplier<T> ifNotNull(Consumer<T> f) {
		if (isDone()) {
			if (!isFailed()) {
				T v = peek();
				if (v != null) f.accept(v);
			}
			return this;
		} else {
			return onSuccess(v -> {
				if (v != null) f.accept(v);
			});
		}
	}

	/**
	 * @noinspection rawtypes
	 */
	default FutureSupplier<T> ifNotDone(CheckedRunnable run) {
		if (!isDone()) {
			try {
				run.run();
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}
		return this;
	}

	@SuppressWarnings("unchecked")
	default <R> FutureSupplier<R> then(
			CheckedFunction<? super T, FutureSupplier<R>, Throwable> then) {
		if (isDone()) {
			if (isFailed()) return (FutureSupplier<R>) this;

			try {
				return then.apply(get());
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}

		var p = new Promise<R>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return super.cancel(mayInterruptIfRunning) ||
						FutureSupplier.this.cancel(mayInterruptIfRunning);
			}
		};

		onCompletion((result, fail) -> {
			if (fail == null) {
				try {
					then.apply(result).onCompletion(p::complete);
				} catch (Throwable ex) {
					p.completeExceptionally(ex);
				}
			} else if (isCancellation(fail)) {
				p.cancel();
			} else {
				p.completeExceptionally(fail);
			}
		});

		return p;
	}

	default <R> FutureSupplier<R> then(
			CheckedFunction<? super T, FutureSupplier<R>, Throwable> onSuccess,
			CheckedFunction<Throwable, FutureSupplier<R>, Throwable> onFailure) {
		if (isDone()) {
			try {
				return isFailed() ? onFailure.apply(getFailure()) : onSuccess.apply(get());
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}

		var p = new Promise<R>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return super.cancel(mayInterruptIfRunning) ||
						FutureSupplier.this.cancel(mayInterruptIfRunning);
			}
		};

		onCompletion((result, fail) -> {
			try {
				if (fail == null) {
					onSuccess.apply(result).onCompletion(p::complete);
				} else {
					onFailure.apply(getFailure()).onCompletion(p::complete);
				}
			} catch (Throwable ex) {
				p.completeExceptionally(ex);
			}
		});

		return p;
	}

	default <R> FutureSupplier<R> thenIgnoreResult(
			CheckedSupplier<FutureSupplier<R>, Throwable> then) {
		if (isDone()) {
			try {
				return then.get();
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}

		var p = new Promise<R>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return super.cancel(mayInterruptIfRunning) ||
						FutureSupplier.this.cancel(mayInterruptIfRunning);
			}
		};

		onCompletion((ignore, fail) -> {
			try {
				if (fail != null) Log.d(fail);
				then.get().onCompletion(p::complete);
			} catch (Throwable ex) {
				p.completeExceptionally(ex);
			}
		});

		return p;
	}

	@SuppressWarnings("unchecked")
	default <R> FutureSupplier<R> closeableMap(
			CheckedFunction<? super T, ? extends R, Throwable> map) {
		if (isDone()) {
			if (isFailed()) return (FutureSupplier<R>) this;

			try (AutoCloseable closeable = (AutoCloseable) get()) {
				return completed(map.apply((T) closeable));
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}

		return ProxySupplier.create(this, result -> {
			try (@SuppressWarnings("unused") AutoCloseable closeable = (AutoCloseable) result) {
				return map.apply(result);
			}
		}, ex -> {
			if (isCancellation(ex)) FutureSupplier.this.cancel();
			throw ex;
		});
	}

	@SuppressWarnings("unchecked")
	default <R> FutureSupplier<R> closeableThen(
			CheckedFunction<? super T, FutureSupplier<R>, Throwable> then) {
		if (isDone()) {
			if (isFailed()) return (FutureSupplier<R>) this;

			try (AutoCloseable closeable = (AutoCloseable) get()) {
				return then.apply((T) closeable);
			} catch (Throwable ex) {
				Log.e(ex);
				return failed(ex);
			}
		}

		var p = new Promise<R>() {
			public boolean cancel(boolean mayInterruptIfRunning) {
				return super.cancel(mayInterruptIfRunning) ||
						FutureSupplier.this.cancel(mayInterruptIfRunning);
			}
		};

		onCompletion((result, fail) -> {
			if (fail == null) {
				try (@SuppressWarnings("unused") AutoCloseable closeable = (AutoCloseable) result) {
					then.apply(result).onCompletion(p::complete);
				} catch (Throwable ex) {
					p.completeExceptionally(ex);
				}
			} else if (isCancellation(fail)) {
				p.cancel();
			} else {
				p.completeExceptionally(fail);
			}
		});

		return p;
	}

	default FutureSupplier<T> thenIterate(
			CheckedFunction<FutureSupplier<T>, FutureSupplier<T>, Throwable> next) {
		return Async.iterate(this, next);
	}

	/**
	 * @noinspection unchecked
	 */
	default FutureSupplier<T> thenIterate(FutureSupplier<T>... next) {
		return Async.iterate(this, next);
	}

	default FutureSupplier<T> thenIterate(Iterable<FutureSupplier<T>> next) {
		return Async.iterate(this, next);
	}

	default FutureSupplier<T> thenIterate(Iterator<FutureSupplier<T>> next) {
		return Async.iterate(this, next);
	}

	default FutureSupplier<T> thenIterate(CheckedSupplier<FutureSupplier<T>, Throwable> next) {
		return Async.iterate(this, next);
	}

	default FutureSupplier<T> thenComplete(Completable<T> complete) {
		return onCompletion(complete::complete);
	}

	/**
	 * @noinspection unchecked
	 */
	default FutureSupplier<T> thenComplete(Completable<T>... complete) {
		return onCompletion(((result, fail) -> {
			for (Completable<T> c : complete) {
				c.complete(result, fail);
			}
		}));
	}

	default FutureSupplier<T> thenRun(Runnable... run) {
		return onCompletion(((result, fail) -> {
			if (fail != null) Log.d(fail);

			for (Runnable r : run) {
				r.run();
			}
		}));
	}

	@SuppressWarnings("rawtypes")
	default FutureSupplier<T> thenReplace(AtomicReferenceFieldUpdater updater, Object owner) {
		return thenReplace(updater, owner, this);
	}

	@SuppressWarnings("rawtypes")
	default FutureSupplier<T> thenReplace(AtomicReferenceFieldUpdater updater, Object owner,
																				Object expect) {
		return thenReplace(updater, owner, expect, Completed::completed);
	}

	@SuppressWarnings("rawtypes")
	default FutureSupplier<T> thenReplaceOrClear(AtomicReferenceFieldUpdater updater, Object owner) {
		return thenReplace(updater, owner, this, Completed::completedOrNull);
	}

	@SuppressWarnings("rawtypes")
	default FutureSupplier<T> thenReplaceOrClear(AtomicReferenceFieldUpdater updater, Object owner,
																							 Object expect) {
		return thenReplace(updater, owner, expect, Completed::completedOrNull);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	default FutureSupplier<T> thenReplace(AtomicReferenceFieldUpdater updater, Object owner,
																				Object expect,
																				Function<FutureSupplier<T>, FutureSupplier<T>> replaceWith) {
		if (isDone()) {
			var replacement = replaceWith.apply(this);
			updater.compareAndSet(owner, expect, replacement);
			if (expect instanceof Completable) ((Completable) expect).completeAs(this);
			return replacement;
		}

		return onCompletion((result, fail) -> {
			var replacement = replaceWith.apply(this);
			updater.compareAndSet(owner, expect, replacement);
			if (expect instanceof Completable) ((Completable) expect).complete(result, fail);
		});
	}

	default <U> FutureSupplier<BiHolder<T, U>> and(FutureSupplier<U> second) {
		return Async.and(this, second);
	}

	default <U> FutureSupplier<?> and(FutureSupplier<U> second,
																		CheckedBiConsumer<T, U, Throwable> consumer) {
		return Async.and(this, second, consumer);
	}

	@SuppressWarnings("unchecked")
	static <T> FutureSupplier<T> noOp() {
		return NoOpSupplier.instance;
	}
}
