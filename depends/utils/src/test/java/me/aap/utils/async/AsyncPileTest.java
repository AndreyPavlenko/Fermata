package me.aap.utils.async;

import androidx.annotation.Nullable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import me.aap.utils.io.AsyncPipe;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.misc.TestUtils;

/**
 * @author Andrey Pavlenko
 */
public class AsyncPileTest extends Assertions {

	@BeforeAll
	public static void setUpClass() {
		TestUtils.enableTestMode();
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testSingleThread(boolean reuseBuf) throws ExecutionException, InterruptedException {
		AsyncPipe pipe = new AsyncPipe(reuseBuf);
		byte[] data = new byte[1024 * 1024];
		ByteBuffer src = ByteBuffer.wrap(data);
		ThreadLocalRandom.current().nextBytes(data);
		MemOutputStream out = new MemOutputStream();
		write(pipe, src, ThreadLocalRandom.current().nextInt(1, 8193), null);
		out.readFrom(pipe).get();
		assertArrayEquals(data, out.getBuffer());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testMultipleThreads(boolean reuseBuf) throws ExecutionException, InterruptedException {
		ExecutorService exec = Executors.newCachedThreadPool();
		AsyncPipe pipe = new AsyncPipe(reuseBuf);
		byte[] data = new byte[50 * 1024 * 1024];
		ByteBuffer src = ByteBuffer.wrap(data);
		ThreadLocalRandom.current().nextBytes(data);
		MemOutputStream out = new MemOutputStream();
		exec.submit(() -> write(pipe, src, ThreadLocalRandom.current().nextInt(512, 8193), exec));
		out.readFrom(pipe).get();
		assertEquals(ByteBuffer.wrap(data), out.getByteBuffer());
		exec.shutdown();
	}

	private void write(AsyncPipe pipe, ByteBuffer src, int bufLen, @Nullable ExecutorService exec) {
		if (src.hasRemaining()) {
			ByteBuffer dup = src.duplicate();
			int n = Math.min(bufLen, src.remaining());
			src.position(src.position() + n);
			dup.limit(dup.position() + n);
			pipe.write(dup).then(v -> {
				if (exec == null) write(pipe, src, bufLen, exec);
				else exec.submit(() -> write(pipe, src, bufLen, exec));
				return Completed.completedVoid();
			});
		} else {
			pipe.endOfStream();
		}
	}
}
