package me.aap.utils.concurrent;

import androidx.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Pavlenko
 */
public class NetThreadPool extends ThreadPool {

	public NetThreadPool(int corePoolSize) {
		super(corePoolSize);
	}

	public NetThreadPool(int corePoolSize, int maximumPoolSize) {
		super(corePoolSize, maximumPoolSize);
	}

	public NetThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit);
	}

	public NetThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
	}

	public NetThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
	}

	@Override
	public Thread newThread(@NonNull Runnable r) {
		NetThread t = new NetThread(r, "NetThread-" + counter.incrementAndGet());
		t.setUncaughtExceptionHandler(this);
		return t;
	}
}
