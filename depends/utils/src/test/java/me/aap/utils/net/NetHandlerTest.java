package me.aap.utils.net;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.log.Log;
import me.aap.utils.misc.TestUtils;

import static me.aap.utils.security.SecurityUtils.sha1;
import static me.aap.utils.security.SecurityUtils.sha1Digest;

/**
 * @author Andrey Pavlenko
 */
public class NetHandlerTest extends Assertions {
	private static ExecutorService exec;
	private static NetHandler handler;
	private static byte[] data;
	private static byte[] checksum;

	@BeforeAll
	public static void setUpClass() throws IOException {
		TestUtils.enableTestMode();
		Random rnd = ThreadLocalRandom.current();
		exec = new NetThreadPool(Runtime.getRuntime().availableProcessors());
		handler = NetHandler.create(o -> o.executor = exec);
		data = new byte[1024 * 1024 * rnd.nextInt(10)];
		rnd.nextBytes(data);
		checksum = sha1(data);
	}

	@AfterAll
	public static void tearDownClass() {
		exec.shutdown();
		data = null;
		checksum = null;
	}

	@RepeatedTest(10)
	public void test() throws Exception {
		int nclients = 100;
		AtomicBoolean failed = new AtomicBoolean();

		NetServer server = handler.bind(o ->
				o.handler = ch -> ch.write(ByteBuffer.wrap(data)).thenRun(ch::close)).get();
		SocketAddress addr = server.getBindAddress();

		FutureSupplier<?>[] tasks = new FutureSupplier[nclients];

		for (int i = 0; i < nclients; i++) {
			int id = i;

			tasks[i] = handler.connect(o -> o.address = addr).then(ch -> {
				MessageDigest md = sha1Digest();

				return ch.read().thenIterate(b -> {
					ByteBuffer bb = b.get();

					if (!bb.hasRemaining()) {
						assertArrayEquals(checksum, md.digest());
						ch.close();
						Log.d("Completed: ", id);
						return null;
					}

					md.update(bb);
					return ch.read();
				});
			}).onFailure(fail -> {
				fail.printStackTrace();
				failed.set(true);
			});
		}

		for (FutureSupplier<?> t : tasks) {
			t.get(20, TimeUnit.SECONDS);
		}

		server.close();
		assertFalse(failed.get());
	}

	@Test
	public void testTimeout() throws Exception {
		try {
			handler.connect(o -> {
				o.host = "10.21.2.2";
				o.port = 80;
				o.connectTimeout = 1;
			}).get(1500, TimeUnit.MILLISECONDS);
			fail();
		} catch (ExecutionException ex) {
			assertTrue(ex.getCause() instanceof TimeoutException);
		}

		NetServer server = handler.bind(o ->
				o.handler = ch -> {
					try {
						Thread.sleep(4000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					ch.write(ByteBuffer.wrap(data)).thenRun(ch::close);
				}).get();
		SocketAddress addr = server.getBindAddress();

		NetChannel ch = handler.connect(o -> {
			o.address = addr;
			o.readTimeout = 1;
			o.writeTimeout = 2;
		}).get();

		try {
			ch.read().get(1500, TimeUnit.MILLISECONDS);
			fail();
		} catch (ExecutionException ex) {
			assertTrue(ex.getCause() instanceof TimeoutException);
		}

		try {
			ch.write(ByteBuffer.wrap(new byte[(int) (Runtime.getRuntime().maxMemory() / 2)]))
					.get(2500, TimeUnit.MILLISECONDS);
			fail();
		} catch (ExecutionException ex) {
			assertTrue(ex.getCause() instanceof TimeoutException);
		}
	}
}
