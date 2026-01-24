package me.aap.utils.app;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.collection.CollectionUtils.forEach;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.concurrent.ThreadPool;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.function.CheckedSupplier;

/**
 * @author Andrey Pavlenko
 */
public class App extends android.app.Application {
	@SuppressLint("StaticFieldLeak")
	private static App instance;
	private volatile HandlerExecutor handler;
	private volatile ThreadPool executor;
	private volatile ScheduledExecutorService scheduler;

	@SuppressWarnings("unchecked")
	public static <C extends App> C get() {
		return (C) instance;
	}

	@Override
	public void onCreate() {
		instance = this;
		super.onCreate();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		instance = null;
		HandlerExecutor h = handler;
		if (h != null) h.close();
		ExecutorService e = executor;
		if (e != null) e.shutdown();
		ScheduledExecutorService s = scheduler;
		if (s != null) forEach(s.shutdownNow(), Runnable::run);
	}

	public String getLogTag() {
		return getApplicationInfo().loadLabel(getPackageManager()).toString();
	}

	@Nullable
	public File getLogFile() {
		return null;
	}

	@Nullable
	public String getCrashReportEmail() {
		return null;
	}

	public int getLogFlushDelay() {
		return 1;
	}

	public int getLogRotateThreshold() {
		return 64 * 1024;
	}

	public HandlerExecutor getHandler() {
		HandlerExecutor h = handler;

		if (h == null) {
			synchronized (this) {
				if ((h = handler) == null) {
					handler = h = new HandlerExecutor(getApplicationContext().getMainLooper());
				}
			}
		}

		return h;
	}

	public void run(Runnable task) {
		if (isMainThread()) {
			task.run();
		} else {
			getHandler().post(task);
		}
	}

	public ThreadPool getExecutor() {
		ThreadPool e = executor;

		if (e == null) {
			synchronized (this) {
				if ((e = executor) == null) {
					executor = e = createExecutor();
				}
			}
		}

		return e;
	}

	public FutureSupplier<?> execute(CheckedRunnable<Throwable> task) {
		return execute(task, null);
	}

	public <T> FutureSupplier<T> execute(CheckedRunnable<Throwable> task, T result) {
		if (isMainThread()) {
			return getExecutor().submitTask(task, result);
		} else {
			try {
				task.run();
				return completed(result);
			} catch (Throwable ex) {
				return failed(ex);
			}
		}
	}

	public <T> FutureSupplier<T> execute(CheckedSupplier<T, Throwable> task) {
		if (isMainThread()) {
			return getExecutor().submitTask(task);
		} else {
			try {
				return completed(task.get());
			} catch (Throwable ex) {
				return failed(ex);
			}
		}
	}

	protected ThreadPool createExecutor() {
		return new ThreadPool(getNumberOfCoreThreads(), getMaxNumberOfThreads(), 60L, TimeUnit.SECONDS);
	}

	protected int getNumberOfCoreThreads() {
		return getMaxNumberOfThreads();
	}

	protected int getMaxNumberOfThreads() {
		return 1;
	}

	public ScheduledExecutorService getScheduler() {
		ScheduledExecutorService s = scheduler;

		if (s == null) {
			synchronized (this) {
				if ((s = scheduler) == null) {
					scheduler = s = createScheduler();
				}
			}
		}

		return s;
	}

	protected ScheduledExecutorService createScheduler() {
		return new ScheduledThreadPoolExecutor(1, r -> {
			Thread t = new Thread(r, "Scheduler");
			t.setDaemon(true);
			return t;
		});
	}

	public boolean hasManifestPermission(String perm) {
		try {
			String[] perms = getPackageManager().getPackageInfo(getPackageName(),
					PackageManager.GET_PERMISSIONS).requestedPermissions;
			return CollectionUtils.contains(perms, perm);
		} catch (PackageManager.NameNotFoundException ignore) {
			return false;
		}
	}
}
