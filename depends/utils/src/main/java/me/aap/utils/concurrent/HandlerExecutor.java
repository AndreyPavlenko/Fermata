package me.aap.utils.concurrent;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

import me.aap.utils.BuildConfig;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class HandlerExecutor extends Handler implements Executor {
	private final Task queue = new Task();
	private volatile boolean closed;

	public HandlerExecutor() {
	}

	public HandlerExecutor(@Nullable Callback callback) {
		super(callback);
	}

	public HandlerExecutor(@NonNull Looper looper) {
		super(looper);
	}

	public HandlerExecutor(@NonNull Looper looper, @Nullable Callback callback) {
		super(looper, callback);
	}

	@Override
	public void execute(Runnable command) {
		post(command);
	}

	public Cancellable submit(@NonNull Runnable task) {
		return schedule(task, 0);
	}

	public synchronized Cancellable schedule(@NonNull Runnable task, long delay) {
		if (isClosed()) {
			Log.d("Handler is closed! Unable to submit task: ", task);
			return () -> false;
		}

		Task t = new Task(task);
		postDelayed(t, delay);
		return t;
	}

	public synchronized void close() {
		if (!isClosed()) {
			closed = true;
			while (queue.next != null) queue.next.cancel();
		}
	}

	public boolean isClosed() {
		return closed;
	}

	private final class Task implements Runnable, Cancellable {
		private Runnable task;
		private Task prev;
		private Task next;

		Task() {
			task = this;
		}

		private Task(@NonNull Runnable task) {
			if (BuildConfig.D && (task == null)) throw new RuntimeException();
			this.task = task;
			prev = queue;
			next = queue.next;
			if (next != null) next.prev = this;
			queue.next = this;
		}

		@Override
		public void run() {
			if (BuildConfig.D && (task == this)) throw new RuntimeException();
			Runnable t = remove();
			if (t == null) Log.e("Task is already done or canceled: ", t);
			else if (isClosed()) Log.e("Executor is closed! Ignoring task: ", t);
			else t.run();
		}

		@Override
		public boolean cancel() {
			if (BuildConfig.D && (task == this)) throw new RuntimeException();
			Runnable t = remove();
			if (t == null) return false;
			removeCallbacks(this);
			return true;
		}

		private Runnable remove() {
			if (task == null) return null;
			synchronized (HandlerExecutor.this) {
				Runnable t = task;
				if (t == null) return null;
				if (prev != null) prev.next = next;
				if (next != null) next.prev = prev;
				task = null;
				return t;
			}
		}
	}
}
