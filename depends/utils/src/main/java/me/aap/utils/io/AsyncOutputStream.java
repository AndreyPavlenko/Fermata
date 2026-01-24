package me.aap.utils.io;

import static me.aap.utils.async.Completed.failed;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface AsyncOutputStream extends Closeable {

	FutureSupplier<Void> write(ByteBuffer src);

	default void flush() throws IOException {
	}

	@Override
	void close();

	default boolean isAsync() {
		return true;
	}

	default void endOfStream() {
	}

	static AsyncOutputStream from(OutputStream out) {
		return from(out, 8192);
	}

	static AsyncOutputStream from(OutputStream out, int bufferLen) {
		return new AsyncOutputStream() {

			@Override
			public FutureSupplier<Void> write(ByteBuffer src) {
				try {
					int len = src.remaining();

					if (src.hasArray()) {
						byte[] a = src.array();
						out.write(a, src.arrayOffset() + src.position(), len);
						src.position(src.position() + len);
					} else {
						byte[] a = new byte[Math.min(len, bufferLen)];

						for (int l = a.length; l > 0; l = Math.min(src.remaining(), a.length)) {
							src.get(a, 0, l);
							out.write(a, 0, l);
						}
					}

					return Completed.completedNull();
				} catch (IOException ex) {
					return failed(ex);
				}
			}

			@Override
			public void flush() throws IOException {
				out.flush();
			}

			@Override
			public void close() {
				IoUtils.close(out);
			}

			@Override
			public boolean isAsync() {
				return false;
			}

			@Override
			public OutputStream asOutputStream() {
				return out;
			}
		};
	}

	default OutputStream asOutputStream() {
		return new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				write(new byte[]{(byte) b});
			}

			@Override
			public void write(@NonNull byte[] b, int off, int len) throws IOException {
				try {
					ByteBuffer buf = ByteBuffer.wrap(b, off, len);
					while (buf.hasRemaining()) AsyncOutputStream.this.write(buf).get();
				} catch (Exception ex) {
					throw new IOException(ex);
				}
			}

			@Override
			public void flush() throws IOException {
				AsyncOutputStream.this.flush();
			}

			@Override
			public void close() {
				AsyncOutputStream.this.close();
			}
		};
	}
}
