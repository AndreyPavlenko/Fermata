package me.aap.utils.concurrent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.text.SharedTextBuilder;

import static me.aap.utils.misc.Assert.assertSame;

/**
 * @author Andrey Pavlenko
 */
public class PooledThread extends Thread {
	private final SharedTextBuilder sb = SharedTextBuilder.create(this);

	public PooledThread() {
	}

	public PooledThread(@Nullable Runnable target) {
		super(target);
	}

	public PooledThread(@Nullable Runnable target, @NonNull String name) {
		super(target, name);
	}

	public SharedTextBuilder getSharedTextBuilder() {
		assertSame(this, Thread.currentThread());
		return sb;
	}
}
