package me.aap.utils.concurrent;

import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.concurrent.locks.LockSupport;

import me.aap.utils.BuildConfig;
import me.aap.utils.app.App;
import me.aap.utils.function.Consumer;
import me.aap.utils.misc.TestUtils;

/**
 * @author Andrey Pavlenko
 */
public class ConcurrentUtils {

	public static boolean isMainThread() {
		return !TestUtils.isTestMode() && Looper.getMainLooper().isCurrentThread();
	}

	public static void ensureMainThread(boolean debug) {
		if (debug && !BuildConfig.D) return;
		if (!isMainThread()) throw new AssertionError("Not the main thread");
	}

	public static void ensureNotMainThread(boolean debug) {
		if (debug && !BuildConfig.D) return;
		if (isMainThread()) throw new AssertionError("Main thread");
	}

	public static <T> void consumeInMainThread(@Nullable Consumer<T> c, T t) {
		if (c != null) {
			if (isMainThread()) c.accept(t);
			else App.get().getHandler().post(() -> c.accept(t));
		}
	}

	public static void wait(Object monitor) throws InterruptedException {
		if (BuildConfig.D && isMainThread()) {
			new IllegalStateException("Waiting on the main thread!").printStackTrace();
		}
		monitor.wait();
	}

	public static void wait(Object monitor, long timeout) throws InterruptedException {
		if (BuildConfig.D && isMainThread()) {
			new IllegalStateException("Waiting on the main thread!").printStackTrace();
		}
		monitor.wait(timeout);
	}

	public static void park() {
		if (BuildConfig.D && isMainThread()) {
			new IllegalStateException("Parking on the main thread!").printStackTrace();
		}

		LockSupport.park();
	}

	public static void parkNanos(long nanos) {
		if (BuildConfig.D && isMainThread()) {
			new IllegalStateException("Parking on the main thread!").printStackTrace();
		}

		LockSupport.parkNanos(nanos);
	}
}
