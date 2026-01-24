package me.aap.utils.concurrent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

import me.aap.utils.misc.TestUtils;

/**
 * @author Andrey Pavlenko
 */
public class ConcurrentLinkedQueueTest extends Assertions {
	private static ThreadPool exec;

	@BeforeAll
	public static void setUpClass() {
		TestUtils.enableTestMode();
		exec = new ThreadPool(Runtime.getRuntime().availableProcessors());
	}

	@AfterAll
	public static void tearDownClass() {
		exec.shutdown();
	}

	@RepeatedTest(10)
	public void test() throws Exception {
		int iters = 100000;
		TestQueue queue = new TestQueue();
		AtomicIntegerArray flags = new AtomicIntegerArray(iters);
		Semaphore sem = new Semaphore(0);
		AtomicBoolean failed = new AtomicBoolean();

		for (int i = 0; i < iters; i++) {
			int n = i;

			exec.submit(() -> {
				assertEquals(0, flags.get(n));
				assertTrue(flags.compareAndSet(n, 0, 1));
				queue.offer(n);
				Integer r = queue.poll();
				assertNotNull(r);
				assertEquals(1, flags.get(r));
				assertTrue(flags.compareAndSet(r, 1, 2));
				sem.release();
			}).onFailure(ex -> {
				ex.printStackTrace();
				failed.set(true);
			});
		}

		sem.acquire(iters);
		assertFalse(failed.get());
		assertEquals(0, queue.size());
		assertTrue(queue.isEmpty());

		for (int i = 0; i < iters; i++) {
			assertEquals(2, flags.get(i));
		}
	}

	static final class TestQueue extends ConcurrentQueueBase<Integer, ConcurrentQueueBase.GenericNode<Integer>> {
		@Override
		protected GenericNode<Integer> newNode(Integer value) {
			return new GenericNode<>(value);
		}
	}
}
