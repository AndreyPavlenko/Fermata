package me.aap.utils.async;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CancellationException;

import me.aap.utils.BuildConfig;
import me.aap.utils.function.CheckedFunction;

import static me.aap.utils.async.CompletableSupplier.Cancelled.CANCELLED;

/**
 * @author Andrey Pavlenko
 */
public abstract class ProxySupplier<C, S> extends CompletableSupplier<C, S> implements CompletableConsumer<C> {

	protected ProxySupplier() {
	}

	protected ProxySupplier(@Nullable FutureSupplier<? extends C> supplier) {
		if (supplier != null) supplier.addConsumer(this);
	}

	public abstract S map(C value) throws Throwable;

	public static <T> ProxySupplier<T, T> create() {
		return create((FutureSupplier<? extends T>) null);
	}

	public static <T> ProxySupplier<T, T> create(FutureSupplier<? extends T> supplier) {
		return new ProxySupplier<T, T>(supplier) {
			@Override
			public T map(T t) {
				return t;
			}
		};
	}

	public static <T, R> ProxySupplier<T, R> create(CheckedFunction<? super T, ? extends R, Throwable> map) {
		return create(null, map);
	}

	public static <T, R> ProxySupplier<T, R> create(@Nullable FutureSupplier<? extends T> supplier,
																									@NonNull CheckedFunction<? super T, ? extends R, Throwable> map) {
		return new ProxySupplier<T, R>(supplier) {
			@Override
			public R map(T t) throws Throwable {
				return map.apply(t);
			}
		};
	}

	public static <T, R> ProxySupplier<T, R> create(@Nullable FutureSupplier<? extends T> supplier,
																									@NonNull CheckedFunction<? super T, ? extends R, Throwable> map,
																									@NonNull CheckedFunction<Throwable, ? extends T, Throwable> onFail) {
		return new ProxySupplier<T, R>(supplier) {
			@Override
			public R map(T t) throws Throwable {
				return map.apply(t);
			}

			@Override
			public boolean completeExceptionally(@NonNull Throwable fail) {
				try {
					return complete(onFail.apply(fail));
				} catch (Throwable ex) {
					return super.completeExceptionally(ex);
				}
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				try {
					Throwable err = BuildConfig.D ? new CancellationException() : CANCELLED.fail;
					return complete(onFail.apply(err));
				} catch (Throwable ex) {
					return super.completeExceptionally(ex);
				}
			}
		};
	}
}
