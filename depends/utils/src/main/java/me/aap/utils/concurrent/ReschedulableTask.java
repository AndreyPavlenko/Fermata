package me.aap.utils.concurrent;

import me.aap.utils.app.App;
import me.aap.utils.function.Cancellable;

public abstract class ReschedulableTask implements Runnable, Cancellable {
	private Cancellable cancel = Cancellable.CANCELED;
	private long scheduledTime;

	protected abstract void perform();

	@Override
	public void run() {
		var now = System.currentTimeMillis();
		if (scheduledTime > now) cancel = getHandler().schedule(this, scheduledTime - now);
		else perform();
	}

	public void schedule(long delay) {
		var now = System.currentTimeMillis();
		if (scheduledTime > now) {
			scheduledTime = now + delay;
		} else {
			scheduledTime = now + delay;
			cancel = getHandler().schedule(this, delay);
		}
	}

	protected HandlerExecutor getHandler() {
		return App.get().getHandler();
	}

	@Override
	public boolean cancel() {
		return cancel.cancel();
	}
}
