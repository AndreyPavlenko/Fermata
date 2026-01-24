package me.aap.utils.async;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import me.aap.utils.concurrent.ThreadPool;
import me.aap.utils.misc.TestUtils;

import static me.aap.utils.async.Completed.completed;


/**
 * @author Andrey Pavlenko
 */
public class ObjectPoolTest extends Assertions {
	private static int nthreads;
	private static ThreadPool exec;


	@BeforeAll
	public static void setUpClass() {
		TestUtils.enableTestMode();
		nthreads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
		exec = new ThreadPool(nthreads);
	}

	@AfterEach
	public void tearDown() {
		TestUtils.enableExceptionLogging(true);
	}

	@AfterAll
	public static void tearDownClass() {
		exec.shutdown();
	}

	@RepeatedTest(1)
	public void test() throws Exception {
		TestPool pool = new TestPool(nthreads / 2);
		assertEquals(nthreads / 2, pool.getMaxLength());
		Semaphore sem = new Semaphore(0);
		int iters = 100000;

		for (int i = 0; i < iters; i++) {
			exec.submit(() -> pool.getObject().closeableMap(o -> {
				o.get().run(sem);
				return null;
			}));
		}

		sem.acquire(iters);

		for (int i = 0; (pool.aliveObjects.get() != pool.getIdleLength()) && i < 1000; i++) {
			LockSupport.parkNanos(1000);
		}

		assertEquals(pool.aliveObjects.get(), pool.getLength());
		assertEquals(pool.aliveObjects.get(), pool.getIdleLength());
		assertEquals(0, pool.getQueueLength());

		pool.close();
		assertEquals(0, pool.aliveObjects.get());
		assertEquals(0, pool.getLength());
		assertEquals(0, pool.getIdleLength());
		assertEquals(0, pool.getQueueLength());
	}

	@RepeatedTest(100)
	public void testClose() throws Exception {
		TestUtils.enableExceptionLogging(false);
		TestPool pool = new TestPool(nthreads / 2);
		Semaphore sem = new Semaphore(0);
		int iters = 1000;

		for (int i = 0; i < iters; i++) {
			exec.submit(() -> pool.getObject().onFailure(fail -> sem.release()).closeableMap(o -> {
				o.get().run(sem);
				return null;
			}));
		}

		pool.close();
		sem.acquire(iters);

		for (int i = 0; (pool.aliveObjects.get() != 0) && i < 1000; i++) {
			LockSupport.parkNanos(1000);
		}

		assertEquals(0, pool.aliveObjects.get());
		assertEquals(0, pool.getLength());
		assertEquals(0, pool.getQueueLength());
	}

	private static final class TestPool extends ObjectPool<TestObject> {
		final AtomicInteger aliveObjects = new AtomicInteger();

		public TestPool(int max) {
			super(max);
		}

		@Override
		protected FutureSupplier<TestObject> createObject() {
			aliveObjects.incrementAndGet();
			return completed(new TestObject());
		}

		@Override
		protected void destroyObject(TestObject obj) {
			aliveObjects.decrementAndGet();
		}

		@Override
		protected boolean validateObject(TestObject obj, boolean releasing) {
			return obj.counter.get() < TestObject.max;
		}
	}

	private static final class TestObject {
		static final int max = 100;
		final AtomicInteger counter = new AtomicInteger();

		void run(Semaphore sem) {
			if (counter.incrementAndGet() > max) throw new IllegalStateException();
			LockSupport.parkNanos(100);
			sem.release();
		}
	}
}
