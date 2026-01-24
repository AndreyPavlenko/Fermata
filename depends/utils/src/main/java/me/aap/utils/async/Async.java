package me.aap.utils.async;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import me.aap.utils.app.App;
import me.aap.utils.function.CheckedBiConsumer;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.log.Log;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public class Async {

	public static <T> FutureSupplier<Void> forEach(CheckedFunction<T, FutureSupplier<?>, Throwable> apply, Iterable<T> it) {
		return forEach(apply, it.iterator());
	}

	@SuppressWarnings("unchecked")
	public static <T> FutureSupplier<Void> forEach(CheckedFunction<T, FutureSupplier<?>, Throwable> apply, Iterator<T> it) {
		try {
			while (it.hasNext()) {
				FutureSupplier<?> s = apply.apply(it.next());
				if (s == null) break;

				if (s.isDone()) {
					if (s.isFailed()) return (FutureSupplier<Void>) s;
				} else {
					return new AsyncIterator.Void(s, c -> it.hasNext() ? apply.apply(it.next()) : null);
				}
			}
		} catch (Throwable ex) {
			return failed(ex);
		}

		return completedVoid();
	}

	@SuppressWarnings("unchecked")
	public static <T> FutureSupplier<Void> forEach(CheckedFunction<T, FutureSupplier<?>, Throwable> apply, T... items) {
		try {
			for (int i = 0; i < items.length; i++) {
				FutureSupplier<?> s = apply.apply(items[i]);
				if (s == null) break;

				if (s.isDone()) {
					if (s.isFailed()) return (FutureSupplier<Void>) s;
				} else {
					Iterator<T> it = (i == (items.length - 1)) ? Collections.emptyIterator() :
							Arrays.asList(items).subList(i + 1, items.length).iterator();
					return new AsyncIterator.Void(s, c -> it.hasNext() ? apply.apply(it.next()) : null);
				}
			}
		} catch (Throwable ex) {
			return failed(ex);
		}

		return completedVoid();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static FutureSupplier<?> all(FutureSupplier<?> first, FutureSupplier<?>... next) {
		return iterate((FutureSupplier) first, Arrays.asList((FutureSupplier[]) next));
	}

	@SuppressWarnings("unchecked")
	public static <T> FutureSupplier<T> iterate(FutureSupplier<T> first, FutureSupplier<T>... next) {
		return iterate(first, Arrays.asList(next));
	}

	public static <T> FutureSupplier<T> iterate(FutureSupplier<T> first, Iterable<FutureSupplier<T>> next) {
		return iterate(first, next.iterator());
	}

	public static <T> FutureSupplier<T> iterate(FutureSupplier<T> first, Iterator<FutureSupplier<T>> next) {
		return iterate(first, c -> next.hasNext() ? next.next() : null);
	}

	public static <T> FutureSupplier<T> iterate(FutureSupplier<T> first, CheckedSupplier<FutureSupplier<T>, Throwable> next) {
		return iterate(first, c -> next.get());
	}

	public static <T> FutureSupplier<T> iterate(Iterable<FutureSupplier<T>> futures) {
		return iterate(futures.iterator());
	}

	public static <T> FutureSupplier<T> iterate(Iterator<FutureSupplier<T>> iterator) {
		return iterate(iterator.next(), iterator);
	}

	public static <T> FutureSupplier<T> iterate(CheckedSupplier<FutureSupplier<T>, Throwable> supplier) {
		try {
			return iterate(supplier.get(), supplier);
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> FutureSupplier<T> iterate(FutureSupplier<T> first, CheckedFunction<FutureSupplier<T>, FutureSupplier<T>, Throwable> next) {
		if (first == null) return completedNull();

		try {
			for (FutureSupplier<T> s = first; ; ) {
				if (s.isDone()) {
					if (s.isFailed()) {
						return s;
					} else {
						FutureSupplier<T> n = next.apply(s);
						if (n == null) return s;
						s = n;
					}
				} else {
					return new AsyncIterator(s, next);
				}
			}
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	public static <T> FutureSupplier<T> retry(CheckedSupplier<FutureSupplier<T>, Throwable> task) {
		return retry(task, ex -> task.get());
	}

	public static <T> FutureSupplier<T> retry(CheckedSupplier<FutureSupplier<T>, Throwable> task,
																						CheckedFunction<Throwable, FutureSupplier<T>, Throwable> retry) {
		FutureSupplier<T> s;

		try {
			s = task.get();
		} catch (Throwable ex) {
			try {
				Log.d(ex, "Task failed, retrying ...");
				return retry.apply(ex);
			} catch (Throwable ex1) {
				return failed(ex1);
			}
		}

		if (s.isDone()) {
			if (!s.isFailed() || s.isCancelled()) return s;

			Throwable ex = s.getFailure();
			Log.d(ex, "Task failed, retrying ...");

			try {
				return retry.apply(ex);
			} catch (Throwable ex1) {
				return failed(ex1);
			}
		}

		ProxySupplier<T, T> proxy = new ProxySupplier<T, T>() {
			FutureSupplier<T> supplier = s;
			boolean retrying;

			@Override
			public T map(T value) {
				return value;
			}

			@Override
			public boolean completeExceptionally(@NonNull Throwable ex) {
				if (retrying) return super.completeExceptionally(ex);
				if (isDone()) return false;

				Log.d(ex, "Task failed, retrying ...");
				retrying = true;

				try {
					supplier = retry.apply(ex);

					if (supplier.isDone()) {
						if (supplier.isFailed()) return super.completeExceptionally(supplier.getFailure());
						else return complete(supplier.peek());
					}

					supplier.addConsumer(this);
					return true;
				} catch (Throwable fail) {
					return super.completeExceptionally(fail);
				}
			}

			public boolean cancel(boolean mayInterruptIfRunning) {
				return super.cancel(mayInterruptIfRunning) || supplier.cancel();
			}
		};

		s.addConsumer(proxy);
		return proxy;
	}

	public static <T, U> FutureSupplier<BiHolder<T, U>> and(FutureSupplier<T> first, FutureSupplier<U> second) {
		if (first.isDone()) {
			if (first.isFailed()) return failed(first.getFailure());

			if (second.isDone()) {
				if (second.isFailed()) return failed(second.getFailure());
				return completed(new BiHolder<>(first.peek(), second.peek()));
			}

			T t = first.peek();
			return second.map(u -> new BiHolder<>(t, second.peek()));
		}

		return first.then(t -> {
			if (second.isDone()) {
				if (second.isFailed()) return failed(second.getFailure());
				return completed(new BiHolder<>(t, second.peek()));
			}

			return second.map(u -> new BiHolder<>(t, second.peek()));
		});
	}

	public static <T, U> FutureSupplier<?> and(FutureSupplier<T> first, FutureSupplier<U> second,
																						 CheckedBiConsumer<T, U, Throwable> consumer) {
		if (first.isDone()) {
			if (first.isFailed()) return failed(first.getFailure());

			if (second.isDone()) {
				if (second.isFailed()) return failed(second.getFailure());

				try {
					consumer.accept(first.peek(), second.peek());
					return completedVoid();
				} catch (Throwable ex) {
					return failed(ex);
				}
			}

			T t = first.peek();
			return second.onSuccess(u -> {
				try {
					consumer.accept(t, u);
				} catch (Throwable ex) {
					Log.e(ex);
				}
			});
		}

		return first.then(t -> {
			if (second.isDone()) {
				if (second.isFailed()) return failed(second.getFailure());

				try {
					consumer.accept(t, second.peek());
					return completedVoid();
				} catch (Throwable ex) {
					return failed(ex);
				}
			}

			return second.then(u -> {
				try {
					consumer.accept(t, u);
					return completedVoid();
				} catch (Throwable ex) {
					return failed(ex);
				}
			});
		});
	}

	public static <T> FutureSupplier<T> scheduleAt(CheckedSupplier<FutureSupplier<T>, Throwable> s,
																								 long time) {
		return schedule(s, time - System.currentTimeMillis());
	}

	public static <T> FutureSupplier<T> schedule(CheckedSupplier<FutureSupplier<T>, Throwable> s,
																							 long delay) {
		Promise<T> p = new Promise<>();
		App.get().getScheduler().schedule(() -> {
			try {
				s.get().thenComplete(p);
			} catch (Throwable ex) {
				p.completeExceptionally(ex);
			}
		}, delay, MILLISECONDS);
		return p;
	}
}
