package me.aap.utils.net;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.ProgressiveResultConsumer.Completion;
import me.aap.utils.holder.Holder;
import me.aap.utils.io.RandomAccessChannel;

import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public interface NetChannel extends Closeable {

	NetHandler getHandler();

	/**
	 * If it's required that consumer and supplier to be called in the same thread, the consumer
	 * argument must be specified. Adding consumer to the returned FutureSupplier does not make any
	 * guarantee on the caller's thread.
	 */
	FutureSupplier<ByteBuffer> read(ByteBufferSupplier supplier, @Nullable Completion<ByteBuffer> consumer);

	default FutureSupplier<ByteBuffer> read(ByteBufferSupplier supplier) {
		return read(supplier, null);
	}

	default FutureSupplier<ByteBuffer> read(ByteBuffer buf) {
		return read(buf, null);
	}

	default FutureSupplier<ByteBuffer> read(ByteBuffer buf, @Nullable Completion<ByteBuffer> consumer) {
		return read(() -> buf, consumer);
	}

	default FutureSupplier<ByteBuffer> read() {
		return read((Completion<ByteBuffer>) null);
	}

	default FutureSupplier<ByteBuffer> read(@Nullable Completion<ByteBuffer> consumer) {
		return read(() -> ByteBuffer.allocate(4096), consumer);
	}

	FutureSupplier<Void> write(ByteBufferArraySupplier supplier, @Nullable Completion<Void> consumer);

	default FutureSupplier<Void> write(ByteBufferArraySupplier supplier) {
		return write(supplier, null);
	}

	default FutureSupplier<Void> write(ByteBufferSupplier supplier) {
		return write(supplier.asArray(), null);
	}

	default FutureSupplier<Void> write(ByteBuffer... buf) {
		return write(() -> buf);
	}

	default FutureSupplier<Void> send(RandomAccessChannel ch, long off, long len) {
		return send(ch, off, len, null);
	}

	default FutureSupplier<Void> send(RandomAccessChannel ch, long off, long len,
																		@Nullable ByteBufferArraySupplier headerSupplier) {
		return send(ch, off, len, headerSupplier, null);
	}

	default FutureSupplier<Void> send(RandomAccessChannel ch, long off, long len,
																		@Nullable ByteBufferArraySupplier headerSupplier,
																		@Nullable Completion<Void> consumer) {
		long[] pos = new long[]{off, len};
		Holder<ByteBufferArraySupplier> h = (headerSupplier != null) ? new Holder<>(headerSupplier) : null;
		ByteBuffer bb = ByteBuffer.allocate(Math.min((int) len, 8192));

		FutureSupplier<Void> f = Async.iterate(() -> {
			if (pos[1] == 0) return null;

			bb.position(0).limit(Math.min((int) pos[1], bb.capacity()));

			try {
				for (int i = 0; i < 10; i++) {
					int n = ch.read(bb, pos[0]);

					if (n == -1) {
						return failed(new IOException("Failed to read file at position " + pos[0]));
					} else if (n > 0) {
						pos[0] += n;
						pos[1] -= n;
						break;
					}
				}
			} catch (Throwable ex) {
				return failed(ex);
			}

			if (bb.position() == 0) failed(new IOException("Failed to read file at position " + pos[0]));

			bb.flip();

			if ((h != null) && (h.value != null)) {
				ByteBufferArraySupplier s = h.value;
				h.value = null;
				return write(s).then(v -> write(bb));
			} else {
				return write(bb);
			}
		});

		if (consumer != null) f.onCompletion(consumer);
		return f;
	}

	boolean isOpen();

	@Override
	void close();

	void setCloseListener(CloseListener listener);

	interface CloseListener {
		void channelClosed(NetChannel channel);
	}
}
