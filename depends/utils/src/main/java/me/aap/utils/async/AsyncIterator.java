package me.aap.utils.async;

import androidx.annotation.NonNull;

import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.ProgressiveResultConsumer;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

/**
 * @author Andrey Pavlenko
 */
public class AsyncIterator<T> extends Promise<T> implements ProgressiveResultConsumer<T> {
	private volatile FutureSupplier<? extends T> current;
	private final CheckedFunction<FutureSupplier<? extends T>, FutureSupplier<? extends T>, Throwable> next;

	public AsyncIterator(FutureSupplier<? extends T> first,
											 CheckedFunction<FutureSupplier<? extends T>, FutureSupplier<? extends T>, Throwable> next) {
		this.current = first;
		this.next = next;
		current.addConsumer(this);
	}

	@Override
	public void accept(T result, Throwable fail, int progress, int total) {
		FutureSupplier<? extends T> s = current;
		if ((s == null) || isDone()) return;

		if (fail != null) {
			if (isCancellation(fail)) cancel();
			else completeExceptionally(fail);
		} else if (s.isDone()) {
			try {
				FutureSupplier<? extends T> n = next.apply(s);

				if (n == null) {
					complete(s.get());
					return;
				}

				current = n;

				if (isDone()) {
					if (isCancelled()) n.cancel();
					current = null;
					return;
				}

				loop(n);
			} catch (Throwable ex) {
				completeExceptionally(ex);
			}
		}
	}


	private void loop(FutureSupplier<? extends T> start) {
		try {
			for (FutureSupplier<? extends T> s = start; ; ) {
				if (s.isDone()) {
					if (s.isCancelled()) {
						cancel();
						return;
					} else if (s.isFailed()) {
						completeExceptionally(requireNonNull(s.getFailure()));
						return;
					} else {
						FutureSupplier<? extends T> n = next.apply(s);

						if (n == null) {
							complete(s.get());
							return;
						}

						current = s = n;

						if (isDone()) {
							if (isCancelled()) n.cancel();
							current = null;
							return;
						}
					}
				} else {
					s.addConsumer(this);
					return;
				}
			}
		} catch (Throwable ex) {
			completeExceptionally(ex);
		}
	}

	@Override
	public boolean complete(T value) {
		if (!super.complete(value)) return false;
		current = null;
		return true;
	}

	@Override
	public boolean completeExceptionally(@NonNull Throwable ex) {
		if (!super.completeExceptionally(ex)) return false;
		current = null;
		return true;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (!super.cancel(mayInterruptIfRunning)) return false;

		FutureSupplier<? extends T> s = current;

		if (s != null) {
			s.cancel();
			current = null;
		}

		return true;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	static final class Void extends AsyncIterator {

		public Void(FutureSupplier first, CheckedFunction<FutureSupplier, FutureSupplier, Throwable> next) {
			super(first, next);
		}

		@Override
		public boolean complete(Object value) {
			return super.complete(null);
		}
	}
}
