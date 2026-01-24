package me.aap.utils.async;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.utils.BuildConfig;
import me.aap.utils.concurrent.ConcurrentQueueBase;
import me.aap.utils.function.Consumer;
import me.aap.utils.holder.Holder;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public abstract class ObjectPool<T> implements AutoCloseable {
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater CLOSED = AtomicReferenceFieldUpdater.newUpdater(ObjectPool.class, ClosedException.class, "closed");
	private final Queue<T> objectQueue = new ConcurrentLinkedQueue<>();
	private final PromiseQueue<T> promiseQueue = new PromiseQueue<>();
	private final AtomicInteger counter = new AtomicInteger();
	private volatile ClosedException closed;
	private final int max;

	public ObjectPool(int max) {
		this.max = max;
	}

	public int getLength() {
		return counter.get();
	}

	public int getMaxLength() {
		return max;
	}

	public int getIdleLength() {
		return objectQueue.size();
	}

	public int getQueueLength() {
		return promiseQueue.size();
	}

	public FutureSupplier<PooledObject<T>> getObject() {
		if (isClosed()) return failed(closed);

		for (T obj = objectQueue.poll(); obj != null; obj = objectQueue.poll()) {
			if (validateObject(obj, false)) {
				return completed(newPooledObject(null, obj));
			} else {
				destroyObject(obj);
				counter.decrementAndGet();
			}
		}

		ObjectPromise<T> promise = new ObjectPromise<>();

		for (int count = counter.get(); count < max; count = counter.get()) {
			if (counter.compareAndSet(count, count + 1)) {
				try {
					createObject().onCompletion((result, fail) -> {
						if (fail != null) {
							counter.decrementAndGet();
							Log.d(fail, "Failed to create object");
							promise.completeExceptionally(fail);
							processQueue();
						} else if (!promise.complete(newPooledObject(null, result))) {
							enqueueObject(result);
							processQueue();
						}
					});
					return promise;
				} catch (Throwable ex) {
					Log.d(ex, "Failed to create object");
					counter.decrementAndGet();
					processQueue();
					return failed(ex);
				}
			}
		}

		enqueuePromise(promise);
		processQueue();
		return promise;
	}

	public boolean isClosed() {
		return closed != null;
	}

	@SuppressWarnings("unchecked")
	public void close() {
		if (!isClosed() && CLOSED.compareAndSet(this, null, new ClosedException())) {
			cleanup();
		}
	}

	private void enqueueObject(T obj) {
		objectQueue.offer(obj);
		if (isClosed()) cleanup();
	}

	private void enqueuePromise(ObjectPromise<T> promise) {
		promiseQueue.offerNode(promise);
		if (isClosed()) cleanup();
	}

	private void cleanup() {
		ClosedException closed = this.closed;

		for (T obj = objectQueue.poll(); obj != null; obj = objectQueue.poll()) {
			destroyObject(obj);
			counter.decrementAndGet();
		}

		promiseQueue.clear(p -> p.completeExceptionally(closed));
	}

	protected abstract FutureSupplier<T> createObject();

	protected boolean validateObject(T obj, boolean releasing) {
		return true;
	}

	protected void destroyObject(T obj) {
	}

	protected PooledObject<T> newPooledObject(Object marker, T obj) {
		return new PooledObject<>(this, marker, obj);
	}

	private void releaseObject(PooledObject<T> released, T obj, boolean destroy) {
		for (; ; ) {
			if (destroy || !validateObject(obj, true) || isClosed()) {
				destroyObject(obj);
				counter.decrementAndGet();

				if (released.marker == Thread.currentThread()) {
					released.marker = PooledObject.INVALID;
				} else {
					processQueue();
				}

				return;
			} else if (released.marker == Thread.currentThread()) {
				released.marker = null;
				return;
			}

			if (!promiseQueue.isEmpty()) {
				Thread thread = Thread.currentThread();
				PooledObject<T> pooled = newPooledObject(thread, obj);

				for (ObjectPromise<T> p = promiseQueue.pollNode(); p != null; p = promiseQueue.pollNode()) {
					if (p.complete(pooled)) {
						if (pooled.marker == thread) {
							pooled.marker = null;
							return;
						} else if (pooled.marker == PooledObject.INVALID) {
							processQueue();
							return;
						} else {
							// The object has been released by this thread, attempting to process it again
							released = pooled;
							break;
						}
					}
				}
			} else {
				break;
			}
		}

		enqueueObject(obj);
		processQueue();
	}

	private void processQueue() {
		if (isClosed()) {
			cleanup();
			return;
		}

		loop:
		for (Thread thread = Thread.currentThread(); !promiseQueue.isEmpty(); ) {
			if (isClosed()) {
				cleanup();
				return;
			}

			T obj = objectQueue.poll();

			if (obj != null) {
				if (!validateObject(obj, false)) {
					destroyObject(obj);
					counter.decrementAndGet();
					continue;
				}

				PooledObject<T> pooled = newPooledObject(thread, obj);

				for (ObjectPromise<T> p = promiseQueue.pollNode(); p != null; p = promiseQueue.pollNode()) {
					if (p.complete(pooled)) {
						if (pooled.marker != null) {
							pooled.marker = null;
							continue loop;
						} else if (!validateObject(obj, false)) {
							destroyObject(obj);
							counter.decrementAndGet();
							continue loop;
						} else if (!promiseQueue.isEmpty()) {
							pooled = newPooledObject(thread, obj);
						} else {
							break;
						}
					}
				}

				enqueueObject(obj);
				continue;
			}

			for (int count = counter.get(); count < max; count = counter.get()) {
				if (counter.compareAndSet(count, count + 1)) {
					try {
						Holder<Thread> h = new Holder<>(thread);
						createObject().onCompletion((result, fail) -> {
							if (fail != null) {
								counter.decrementAndGet();
								Log.d(fail, "Failed to create object");
								ObjectPromise<T> p = promiseQueue.pollNode();
								if (p != null) p.completeExceptionally(fail);
							} else {
								enqueueObject(result);
							}

							if (h.value != Thread.currentThread()) processQueue();
						});
						h.value = null;
					} catch (Throwable ex) {
						counter.decrementAndGet();
						Log.d(ex, "Failed to create object");
						ObjectPromise<T> p = promiseQueue.pollNode();
						if (p != null) p.completeExceptionally(ex);
					}

					continue loop;
				}
			}

			return;
		}
	}

	public static class PooledObject<T> implements AutoCloseable {
		static final Object INVALID = new Object();
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater REF =
				AtomicReferenceFieldUpdater.newUpdater(PooledObject.class, Object.class, "ref");
		private final ObjectPool<T> pool;
		private volatile Object ref;
		Object marker;

		protected PooledObject(ObjectPool<T> pool, Object marker, T obj) {
			this.pool = pool;
			this.marker = marker;
			ref = obj;
		}

		@SuppressWarnings("unchecked")
		public T get() {
			return (T) ref;
		}

		public boolean release() {
			return release(false);
		}

		@SuppressWarnings("unchecked")
		private boolean release(boolean destroy) {
			T obj = (T) ref;

			if ((obj != null) && REF.compareAndSet(this, obj, null)) {
				pool.releaseObject(this, obj, destroy);
				return true;
			} else {
				return false;
			}
		}

		boolean destroy() {
			return release(true);
		}

		@Override
		public void close() {
			release();
		}

		@Override
		protected void finalize() {
			if (BuildConfig.D && (ref != null)) {
				Log.w("Pooled object has not been properly released: " + ref);
			}

			release();
		}
	}

	public static final class ClosedException extends RuntimeException {
		ClosedException() {
			super("Object pool closed");
		}

		@Override
		public void printStackTrace() {
		}
	}

	private static final class PromiseQueue<T> extends ConcurrentQueueBase<PooledObject<T>, ObjectPromise<T>> {

		@Override
		protected void offerNode(ObjectPromise<T> node) {
			super.offerNode(node);
		}

		@Override
		protected ObjectPromise<T> pollNode() {
			return super.pollNode();
		}

		@Override
		protected void clear(Consumer<ObjectPromise<T>> c) {
			super.clear(c);
		}
	}

	private static final class ObjectPromise<T> extends Promise<PooledObject<T>> implements ConcurrentQueueBase.Node<PooledObject<T>> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater NEXT = AtomicReferenceFieldUpdater.newUpdater(ObjectPromise.class, ObjectPromise.class, "next");
		private volatile ObjectPromise<T> next;

		@Override
		public ConcurrentQueueBase.Node<PooledObject<T>> getNext() {
			return next;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean compareAndSetNext(ConcurrentQueueBase.Node<PooledObject<T>> expect, ConcurrentQueueBase.Node<PooledObject<T>> update) {
			return NEXT.compareAndSet(this, expect, update);
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (super.cancel(mayInterruptIfRunning)) return true;

			PooledObject<T> obj = peek();
			if (obj != null) obj.release();
			return false;
		}
	}
}
