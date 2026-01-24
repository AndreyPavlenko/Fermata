package me.aap.utils.async;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import me.aap.utils.BuildConfig;
import me.aap.utils.concurrent.ConcurrentUtils;
import me.aap.utils.function.CheckedBiFunction;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.ProgressiveResultConsumer;
import me.aap.utils.function.ResultConsumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.TestUtils;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_DONE;
import static me.aap.utils.misc.Assert.assertNotEquals;
import static me.aap.utils.misc.Assert.assertNotNull;
import static me.aap.utils.misc.Assert.assertTrue;

/**
 * @author Andrey Pavlenko
 */
public abstract class CompletableSupplier<C, S> implements Completable<C>, FutureSupplier<S> {
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<CompletableSupplier, Object> STATE =
			newUpdater(CompletableSupplier.class, Object.class, "state");
	@Keep
	@SuppressWarnings("unused")
	private volatile Object state = Incomplete.INITIAL;
	private final Throwable trace;

	public CompletableSupplier() {
		trace = BuildConfig.FUTURE_TRACE ? new Throwable() : null;
	}

	protected abstract S map(C value) throws Throwable;

	public static <C, S> CompletableSupplier<C, S> create(CheckedFunction<? super C, ? extends S, Throwable> map) {
		return new CompletableSupplier<C, S>() {
			@Override
			protected S map(C value) throws Throwable {
				return map.apply(value);
			}
		};
	}

	public static <C, S> CompletableSupplier<C, S> create(CheckedBiFunction<CompletableSupplier<C, S>, ? super C, ? extends S, Throwable> map) {
		return new CompletableSupplier<C, S>() {
			@Override
			protected S map(C value) throws Throwable {
				return map.apply(this, value);
			}
		};
	}

	@SuppressWarnings("unchecked")
	@Override
	public FutureSupplier<S> addConsumer(@NonNull ProgressiveResultConsumer<? super S> consumer) {
		for (Object st = STATE.get(this); ; st = STATE.get(this)) {
			if (!(st instanceof Incomplete)) {
				if (st instanceof Failed) {
					supply(consumer, null, ((Failed) st).fail, PROGRESS_DONE, PROGRESS_DONE, getExecutor());
				} else {
					supply(consumer, (S) st, null, PROGRESS_DONE, PROGRESS_DONE, getExecutor());
				}
				break;
			}

			Incomplete<S> current = (Incomplete<S>) st;

			if (current.progress == null) {
				if (STATE.compareAndSet(this, st, new Incomplete<>(current, consumer, false))) break;
			} else {
				Incomplete<S> i = new Incomplete<>(current, consumer, true);
				if (!STATE.compareAndSet(this, st, i)) continue;

				if (!current.processing) supplyProgress(i, i.rangeIterator(current));
				break;
			}
		}

		return this;
	}

	@Override
	public boolean complete(@Nullable C value) {
		try {
			return supply(map(value), null, PROGRESS_DONE, PROGRESS_DONE);
		} catch (Throwable ex) {
			Log.d(ex);
			return completeExceptionally(ex);
		}
	}

	@Override
	public boolean completeExceptionally(@NonNull Throwable ex) {
		if (BuildConfig.D && TestUtils.logExceptions() && !isDone()) {
			Log.d(ex, "Completed exceptionally");
		}
		return supply(null, ex, PROGRESS_DONE, PROGRESS_DONE);
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (BuildConfig.D) {
			return supply(null, new CancellationException(), PROGRESS_DONE, PROGRESS_DONE);
		} else {
			return supply(null, Cancelled.CANCELLED.fail, PROGRESS_DONE, PROGRESS_DONE);
		}
	}

	@Override
	public boolean isDone() {
		return !(STATE.get(this) instanceof Incomplete);
	}

	@Override
	public boolean isCancelled() {
		return isCancelled(STATE.get(this));
	}

	private static boolean isCancelled(Object st) {
		if (BuildConfig.D) {
			return (st instanceof Failed) && (((Failed) st).fail instanceof CancellationException);
		} else {
			return st == Cancelled.CANCELLED;
		}
	}

	@Override
	public boolean isFailed() {
		return (STATE.get(this) instanceof Failed);
	}

	@Nullable
	@Override
	public Throwable getFailure() {
		Object st = STATE.get(this);
		return (st instanceof Failed) ? ((Failed) st).fail : null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public S get() throws ExecutionException, InterruptedException {
		long timeout = getTimeout();

		if (timeout > 0) {
			try {
				return get(timeout, TimeUnit.MILLISECONDS);
			} catch (TimeoutException ex) {
				throw new InterruptedException("Interrupted due to timeout: " + timeout);
			}
		}

		Object st = STATE.get(this);

		if (!(st instanceof Incomplete)) {
			if (st instanceof Failed) {
				if (isCancelled(st)) throw new CancellationException();
				throw new ExecutionException(((Failed) st).fail);
			} else {
				return (S) st;
			}
		}

		Thread t = Thread.currentThread();
		addConsumer(new WaitingConsumer<>(t));

		for (; ; ) {
			ConcurrentUtils.park();
			if (t.isInterrupted()) throw new InterruptedException();

			st = STATE.get(this);

			if (!(st instanceof Incomplete)) {
				if (st instanceof Failed) {
					if (isCancelled(st)) throw new CancellationException();
					throw new ExecutionException(((Failed) st).fail);
				} else {
					return (S) st;
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public S get(long timeout, @NonNull TimeUnit unit) throws ExecutionException,
			InterruptedException, TimeoutException {

		Object st = STATE.get(this);

		if (!(st instanceof Incomplete)) {
			if (st instanceof Failed) {
				if (isCancelled(st)) throw new CancellationException();
				throw new ExecutionException(((Failed) st).fail);
			} else {
				return (S) st;
			}
		}

		long startTime = System.nanoTime();
		long waitTime = unit.toNanos(timeout);
		Thread t = Thread.currentThread();
		addConsumer(new WaitingConsumer<>(t));

		for (; ; ) {
			ConcurrentUtils.parkNanos(waitTime);
			if (t.isInterrupted()) throw new InterruptedException();

			st = STATE.get(this);

			if (!(st instanceof Incomplete)) {
				if (st instanceof Failed) {
					if (isCancelled(st)) throw new CancellationException();
					throw new ExecutionException(((Failed) st).fail);
				} else {
					return (S) st;
				}
			}

			waitTime -= System.nanoTime() - startTime;
			if (waitTime <= 0) throw new TimeoutException();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public S peek(@Nullable S ifNotDone) {
		Object st = state;
		if (!(st instanceof Incomplete) && !(st instanceof Failed)) return (S) st;
		return ifNotDone;
	}

	@SuppressWarnings("unchecked")
	@Override
	public S peek(@Nullable Supplier<? extends S> ifNotDone) {
		Object st = state;
		if (!(st instanceof Incomplete) && !(st instanceof Failed)) return (S) st;
		return (ifNotDone != null) ? ifNotDone.get() : null;
	}

	@Override
	public boolean setProgress(C incomplete, int progress, int total) {
		assertNotEquals(progress, PROGRESS_DONE);
		try {
			return supply(map(incomplete), null, progress, total);
		} catch (Throwable ex) {
			return completeExceptionally(ex);
		}
	}

	private void supply(ProgressiveResultConsumer<? super S> consumer, S result, Throwable fail,
											int progress, int total, @Nullable Executor executor) {
		if (BuildConfig.FUTURE_TRACE && (fail != null)) {
			Log.d(trace, "FutureSupplier failed");
		}

		try {
			if ((executor == null) || (consumer instanceof WaitingConsumer)) {
				consumer.accept(result, fail, progress, total);
			} else {
				executor.execute(() -> consumer.accept(result, fail, progress, total));
			}
		} catch (Throwable ex) {
			Log.d(ex);
			completeExceptionally(ex);
			consumer.accept(null, ex);
		}
	}

	private void supply(Iterable<ProgressiveResultConsumer<? super S>> consumers, S result,
											Throwable fail, int progress, int total, @Nullable Executor executor) {
		for (ProgressiveResultConsumer<? super S> c : consumers) {
			supply(c, result, fail, progress, total, executor);
		}
	}

	private boolean supply(S result, Throwable fail, int progress, int total) {
		for (Object st = STATE.get(this); ; st = STATE.get(this)) {
			if (!(st instanceof Incomplete)) return false;

			@SuppressWarnings("unchecked") Incomplete<S> current = (Incomplete<S>) st;

			if (current.processing) {
				assertNotNull(current.progress);
				assert current.progress != null;
				if (current.progress.isDone()) return false;
				Progress<S> p = new Progress<>(result, fail, progress, total);
				if (STATE.compareAndSet(this, st, new Incomplete<>(current, p, true))) return true;
			} else if (progress == PROGRESS_DONE) {
				Object r;

				if (fail != null)
					r = ResultConsumer.Cancel.isCancellation(fail) ? Cancelled.get() : new Failed(fail);
				else r = result;

				if (!STATE.compareAndSet(this, st, r)) continue;

				supply(current, result, fail, progress, total, getExecutor());
				return true;
			} else {
				Progress<S> p = new Progress<>(result, fail, progress, total);
				Incomplete<S> i = new Incomplete<>(current, p, true);
				if (!STATE.compareAndSet(this, st, i)) continue;
				supplyProgress(i, i);
				return true;
			}
		}
	}

	private void supplyProgress(Incomplete<S> current, Iterable<ProgressiveResultConsumer<? super S>> consumers) {
		for (Executor executor = getExecutor(); ; ) {
			Progress<S> p = current.progress;
			assertNotNull(p);
			assert p != null;

			if (p.isDone()) {
				Object r;

				if (p.fail != null) {
					if (Cancelled.isCancellation(p.fail)) r = Cancelled.get();
					else r = new Failed(p.fail);
				} else {
					r = p.result;
				}

				if (STATE.compareAndSet(this, current, r)) {
					supply(current, p.result, p.fail, p.progress, p.total, executor);
					return;
				}
			} else {
				supply(consumers, p.result, p.fail, p.progress, p.total, executor);

				Incomplete<S> i = new Incomplete<>(current, p, false);
				if (STATE.compareAndSet(this, current, i)) return;
			}

			Object st = STATE.get(this);
			assertTrue(st instanceof Incomplete);
			assert st instanceof Incomplete;
			@SuppressWarnings("unchecked") Incomplete<S> top = (Incomplete<S>) st;
			assertTrue(top.processing);
			assertNotNull(top.progress);

			if (top.progress == p) {
				consumers = top.rangeIterator(current);
				current = top;
			} else {
				current = top;
				consumers = top;
			}
		}
	}

	protected long getTimeout() {
		return 0L;
	}

	static final class Failed {
		final Throwable fail;

		Failed(Throwable ex) {
			this.fail = ex;
		}
	}

	interface Cancelled {
		Failed CANCELLED = new Failed(new CancellationException());

		static Failed get() {
			return BuildConfig.D ? new Failed(new CancellationException()) : CANCELLED;
		}

		static boolean isCancellation(Throwable ex) {
			return BuildConfig.D ? (ex instanceof CancellationException) : (ex == CANCELLED.fail);
		}
	}

	private static final class Incomplete<T> implements Iterable<ProgressiveResultConsumer<? super T>> {
		@SuppressWarnings("rawtypes")
		static final Incomplete INITIAL = new Incomplete();
		@NonNull
		final Incomplete<T> id;
		@Nullable
		final Incomplete<T> next;
		@Nullable
		final ProgressiveResultConsumer<? super T> consumer;
		@Nullable
		final Progress<T> progress;
		final boolean processing;

		Incomplete() {
			id = this;
			next = null;
			consumer = null;
			progress = null;
			processing = false;
		}

		private Incomplete(@NonNull Incomplete<T> i) {
			id = i.id;
			next = i.next;
			consumer = i.consumer;
			progress = null;
			processing = false;
		}

		Incomplete(@NonNull Incomplete<T> i, @NonNull ProgressiveResultConsumer<? super T> c, boolean processing) {
			Progress<T> p = i.progress;
			id = this;
			next = (p == null) ? i : new Incomplete<>(i);
			consumer = c;
			progress = p;
			this.processing = processing;
		}

		Incomplete(@NonNull Incomplete<T> i, @NonNull Progress<T> p, boolean processing) {
			id = i.id;
			next = i.next;
			consumer = i.consumer;
			progress = p;
			this.processing = processing;
		}

		@NonNull
		@Override
		public Iterator<ProgressiveResultConsumer<? super T>> iterator() {
			return ConsumerIterator.create(this).iterator();
		}

		public Iterable<ProgressiveResultConsumer<? super T>> rangeIterator(@NonNull Incomplete<T> stop) {
			return ConsumerIterator.create(this, stop);
		}
	}

	private static final class ConsumerIterator<T> implements Iterable<ProgressiveResultConsumer<? super T>>,
			Iterator<ProgressiveResultConsumer<? super T>> {
		final ProgressiveResultConsumer<? super T> consumer;
		final ConsumerIterator<T> next;
		ConsumerIterator<T> current;

		public ConsumerIterator(ProgressiveResultConsumer<? super T> consumer, ConsumerIterator<T> next) {
			this.consumer = consumer;
			this.next = next;
		}

		static <T> Iterable<ProgressiveResultConsumer<? super T>> create(Incomplete<T> start) {
			Incomplete<T> i = start;

			while (i.consumer == null) {
				if ((i = i.next) == null) return Collections.emptyList();
			}

			ConsumerIterator<T> c = new ConsumerIterator<>(i.consumer, null);

			while ((i = i.next) != null) {
				if (i.consumer == null) continue;
				c = new ConsumerIterator<>(i.consumer, c);
			}

			c.current = c;
			return c;
		}

		static <T> Iterable<ProgressiveResultConsumer<? super T>> create(@NonNull Incomplete<T> start, @NonNull Incomplete<T> stop) {
			Incomplete<T> i = start;

			while (i.consumer == null) {
				if (((i = i.next) == null) || (i.id == stop.id)) return Collections.emptyList();
			}

			ConsumerIterator<T> c = new ConsumerIterator<>(i.consumer, null);

			while (((i = i.next) != null) && (i.id != stop.id)) {
				if (i.consumer == null) continue;
				c = new ConsumerIterator<>(i.consumer, c);
			}

			c.current = c;
			return c;
		}

		@NonNull
		@Override
		public Iterator<ProgressiveResultConsumer<? super T>> iterator() {
			return this;
		}

		@Override
		public boolean hasNext() {
			return current != null;
		}

		@Override
		public ProgressiveResultConsumer<? super T> next() {
			if (current == null) throw new NoSuchElementException();
			ProgressiveResultConsumer<? super T> c = current.consumer;
			current = current.next;
			return c;
		}
	}

	private static final class Progress<T> {
		final T result;
		final Throwable fail;
		final int progress;
		final int total;

		Progress(T result, Throwable fail, int progress, int total) {
			assertTrue((fail == null) || (progress == PROGRESS_DONE));
			this.result = result;
			this.fail = fail;
			this.progress = progress;
			this.total = total;
		}

		boolean isDone() {
			return progress == PROGRESS_DONE;
		}
	}

	private static final class WaitingConsumer<T> implements ProgressiveResultConsumer<T> {
		private final Thread thread;

		WaitingConsumer(Thread thread) {
			this.thread = thread;
		}

		@Override
		public void accept(T result, Throwable fail, int progress, int total) {
			if (progress == PROGRESS_DONE) LockSupport.unpark(thread);
		}
	}
}
