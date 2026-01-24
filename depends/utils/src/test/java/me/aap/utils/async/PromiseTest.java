package me.aap.utils.async;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import me.aap.utils.concurrent.ThreadPool;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.misc.TestUtils;

import static java.util.concurrent.TimeUnit.SECONDS;
import static me.aap.utils.async.RunnablePromise.create;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_DONE;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;


/**
 * @author Andrey Pavlenko
 */
public class PromiseTest extends Assertions {
	private static ExecutorService exec;
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final AtomicReferenceFieldUpdater<PromiseTest, FutureSupplier<Integer>> updater =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(PromiseTest.class, FutureSupplier.class, "supplier");
	@SuppressWarnings("FieldCanBeLocal")
	private volatile FutureSupplier<Integer> supplier;

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
	public void testProgress1() throws ExecutionException, InterruptedException, TimeoutException {
		FutureSupplier<Void> forEach = Async.forEach(this::progress, 100000, 10000, 1000);
		forEach.get(5, SECONDS);
	}

	@RepeatedTest(10)
	public void testProgress2() throws ExecutionException, InterruptedException, TimeoutException {
		CheckedFunction<Integer, FutureSupplier<?>, Throwable> next = iters -> {
			RunnablePromise<?> p = create(this::progress, iters);
			exec.submit(p);
			return p;
		};

		FutureSupplier<Void> forEach = Async.forEach(next, 100000, 10000, 1000);
		forEach.get(5, SECONDS);

		forEach = Async.forEach(next, 100000, 10000, 1000);
		assertTrue(forEach.cancel());
		assertFalse(forEach.cancel());
		assertTrue(forEach.isDone());
		assertTrue(forEach.isCancelled());
		assertTrue(forEach.isFailed());
		assertTrue(isCancellation(forEach.getFailure()));

		try {
			forEach.get(5, SECONDS);
			fail();
		} catch (CancellationException ex) {
			// ignore
		}
	}

	public FutureSupplier<Integer> progress(int iters) throws ExecutionException, InterruptedException, TimeoutException {
		Promise<Integer> p = new Promise<>();
		supplier = p.thenReplace(updater, this);
		AtomicInteger counter = new AtomicInteger();
		AtomicBoolean success = new AtomicBoolean();
		AtomicBoolean consumed = new AtomicBoolean();
		AtomicBoolean completion = new AtomicBoolean();
		AtomicBoolean success2 = new AtomicBoolean();
		AtomicBoolean consumed2 = new AtomicBoolean();
		AtomicBoolean completion2 = new AtomicBoolean();

		p.onCancel(Assertions::fail).onFailure(Assertions::fail).onProgress((incomplete, progress, total) -> {
			LockSupport.parkNanos(100);
//			System.out.println("Progress: " + progress);
			assertTrue(progress < iters);
			assertTrue(incomplete < iters);
			assertEquals(total, iters);
		}).onSuccess(result -> {
			assertEquals(result.intValue(), iters);
			success.set(true);
		}).onCompletion(((result, fail) -> {
			assertEquals(result.intValue(), iters);
			assertNull(fail);
			completion.set(true);
		})).addConsumer((result, fail, progress, total) -> {
			if (progress == PROGRESS_DONE) {
				assertEquals(result.intValue(), iters);
				assertNull(fail);
				consumed.set(true);
			} else {
				assertTrue(progress < iters);
				assertTrue(result < iters);
				assertEquals(total, iters);
			}
		});

		for (int i = 0; i < iters; i++) {
			exec.submit(() -> {
				int c = counter.incrementAndGet();
				if (c == iters) p.complete(iters);
				else p.setProgress(c, c, iters);
			});
		}

		p.onCancel(Assertions::fail).onFailure(Assertions::fail).onProgress((incomplete, progress, total) -> {
//			System.out.println("Progress: " + progress);
			assertTrue(progress < iters);
			assertTrue(incomplete < iters);
			assertEquals(total, iters);
		}).onSuccess(result -> {
			assertEquals(result.intValue(), iters);
			success2.set(true);
		}).onCompletion(((result, fail) -> {
			assertEquals(result.intValue(), iters);
			assertNull(fail);
			completion2.set(true);
		})).addConsumer((result, fail, progress, total) -> {
			if (progress == PROGRESS_DONE) {
				assertEquals(result.intValue(), iters);
				assertNull(fail);
				consumed2.set(true);
			} else {
				assertTrue(progress < iters);
				assertTrue(result < iters);
				assertEquals(total, iters);
			}
		});

		assertEquals(p.get(5, SECONDS).intValue(), iters);
		assertTrue(success.get());
		assertTrue(consumed.get());
		assertTrue(completion.get());

		assertEquals(p.get().intValue(), iters);
		assertTrue(success2.get());
		assertTrue(consumed2.get());
		assertTrue(completion2.get());

		AtomicBoolean success3 = new AtomicBoolean();
		AtomicBoolean consumed3 = new AtomicBoolean();
		AtomicBoolean completion3 = new AtomicBoolean();

		p.onCancel(Assertions::fail).onFailure(Assertions::fail)
				.onProgress((incomplete, progress, total) -> fail()).onSuccess(result -> {
			assertEquals(result.intValue(), iters);
			success3.set(true);
		}).onCompletion(((result, fail) -> {
			assertEquals(result.intValue(), iters);
			assertNull(fail);
			completion3.set(true);
		})).addConsumer((result, fail, progress, total) -> {
			if (progress == PROGRESS_DONE) {
				assertEquals(result.intValue(), iters);
				assertNull(fail);
				consumed3.set(true);
			} else {
				fail();
			}
		});

		assertEquals(p.get().intValue(), iters);
		assertTrue(success3.get());
		assertTrue(consumed3.get());
		assertTrue(completion3.get());

		FutureSupplier<Integer> replaced = updater.get(this);
		assertTrue(replaced instanceof Completed, () -> replaced.getClass().getName());
		assertEquals(replaced.get().intValue(), iters);
		return supplier;
	}
}
