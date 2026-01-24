package me.aap.utils.io;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.net.ByteBufferSupplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.io.IoUtils.copyOfRange;
import static me.aap.utils.io.IoUtils.emptyByteBuffer;

/**
 * @author Andrey Pavlenko
 */
public class AsyncPipe implements AsyncInputStream, AsyncOutputStream {
	private static final AtomicReferenceFieldUpdater<AsyncPipe, Object> REF = newUpdater(AsyncPipe.class, Object.class, "promise");
	private final boolean reuseWriteBuf;
	private volatile Object promise;

	public AsyncPipe() {
		this(false);
	}

	public AsyncPipe(boolean reuseWriteBuf) {
		this.reuseWriteBuf = reuseWriteBuf;
	}

	@Override
	public FutureSupplier<ByteBuffer> read() {
		ReadPromise rp = null;

		for (Object p = promise; ; p = promise) {
			if (p == null) {
				if (rp == null) rp = new ReadPromise();
				if (REF.compareAndSet(this, null, rp)) return rp;
			} else if ((p == getLock()) || (p instanceof ReadPromise)) {
				return failed(new IOException("Read pending"));
			} else if (p instanceof WritePromise) {
				if (REF.compareAndSet(this, p, null)) {
					WritePromise wp = (WritePromise) p;
					if (wp.complete(null)) return completed(wp.buff);
				}
			} else if (p instanceof EOS) {
				return completed(emptyByteBuffer());
			} else {
				return ((Closed) p).get();
			}
		}
	}

	@Override
	public FutureSupplier<ByteBuffer> read(ByteBufferSupplier bbs) {
		ReadPromise rp = null;

		for (Object p = promise; ; p = promise) {
			if (p == null) {
				if (rp == null) rp = new ReadPromise(bbs);
				if (REF.compareAndSet(this, null, rp)) return rp;
			} else if ((p == getLock()) || (p instanceof ReadPromise)) {
				return failed(new IOException("Read pending"));
			} else if (p instanceof WritePromise) {
				if (!REF.compareAndSet(this, p, getLock())) continue;

				WritePromise wp = (WritePromise) p;
				ByteBuffer dst = bbs.getByteBuffer();
				int srcPos = wp.buff.position();
				int dstPos = dst.position();
				int srcLim = wp.buff.limit();
				int dstLim = dst.limit();
				int srcRemain = srcLim - srcPos;
				int dstRemain = dstLim - dstPos;

				if (dstRemain >= srcRemain) {
					if (REF.compareAndSet(this, getLock(), null)) {
						if (wp.complete(null)) {
							dst.put(wp.buff);
							dst.position(dstPos).limit(dstPos + srcRemain);
							return completed(dst);
						}
					}
				} else {
					int srcLimit = wp.buff.limit();
					wp.buff.limit(wp.buff.position() + dstRemain);
					dst.put(wp.buff);
					dst.position(dstPos);
					wp.buff.limit(srcLimit);
					if (REF.compareAndSet(this, getLock(), wp)) return completed(dst);
				}

				wp.buff.position(srcPos).limit(srcLim);
				dst.position(dstPos).limit(dstLim);
				bbs.releaseByteBuffer(dst);
				p = promise;

				if (p instanceof Closed) {
					wp.completeExceptionally(requireNonNull(((Closed) p).fail.getFailure()));
					return ((Closed) p).get();
				} else if (p instanceof EOS) {
					wp.completeExceptionally(new IOException("End of stream"));
					ByteBuffer bb = bbs.getByteBuffer();
					bb.limit(bb.position());
					return completed(bb);
				} else {
					wp.completeExceptionally(new IllegalStateException());
					return failed(new IllegalStateException());
				}
			} else if (p instanceof EOS) {
				ByteBuffer bb = bbs.getByteBuffer();
				bb.limit(bb.position());
				return completed(bb);
			} else {
				return ((Closed) p).get();
			}
		}
	}

	@Override
	public FutureSupplier<Void> write(ByteBuffer src) {
		if (!src.hasRemaining()) {
			endOfStream();
			return completedVoid();
		}

		if (!reuseWriteBuf) src = IoUtils.copyOf(src);
		WritePromise wp = null;

		for (Object p = promise; ; p = promise) {
			if (p == null) {
				wp = createWritePromise(wp, src);
				if (REF.compareAndSet(this, null, wp)) return wp;
			} else if ((p == getLock()) || (p instanceof WritePromise)) {
				return failed(new IOException("Write pending"));
			} else if (p instanceof ReadPromise) {
				ReadPromise rp = (ReadPromise) p;

				if (rp.bbs == null) {
					if (REF.compareAndSet(this, p, null) && rp.complete(src)) return completedVoid();
				} else if (REF.compareAndSet(this, p, getLock())) {
					ByteBuffer dst = rp.bbs.getByteBuffer();
					int srcPos = src.position();
					int dstPos = dst.position();
					int srcLim = src.limit();
					int dstLim = dst.limit();
					int srcRemain = srcLim - srcPos;
					int dstRemain = dstLim - dstPos;

					if (dstRemain >= srcRemain) {
						if (REF.compareAndSet(this, getLock(), null)) {
							dst.put(src);
							dst.position(dstPos).limit(dstPos + srcRemain);
							if (rp.complete(dst)) return completedVoid();
						}
					} else {
						src.limit(src.position() + dstRemain);
						dst.put(src);
						dst.position(dstPos);
						src.limit(srcLim);
						wp = createWritePromise(wp, src);
						if (REF.compareAndSet(this, getLock(), wp) && rp.complete(dst)) return wp;
					}

					src.position(srcPos).limit(srcLim);
					dst.position(dstPos).limit(dstLim);
					rp.bbs.releaseByteBuffer(dst);
					p = promise;

					if (p instanceof Closed) {
						rp.completeExceptionally(requireNonNull(((Closed) p).fail.getFailure()));
						return ((Closed) p).get();
					} else if (p instanceof EOS) {
						supplyEos(rp);
						return failed(new IOException("End of stream"));
					} else {
						rp.completeExceptionally(new IllegalStateException());
						return failed(new IllegalStateException());
					}
				}
			} else if (p instanceof EOS) {
				return failed(new IOException("End of stream"));
			} else {
				return ((Closed) p).get();
			}
		}
	}

	private WritePromise createWritePromise(WritePromise wp, ByteBuffer src) {
		if (wp == null) {
			wp = new WritePromise(src);
			wp.onFailure(this::close);
		}

		return wp;
	}

	@Override
	public int available() {
		Object p = promise;

		if (p instanceof WritePromise) {
			WritePromise wp = (WritePromise) p;
			ByteBuffer buf = wp.buff;
			return (buf != null) && !wp.isDone() ? buf.remaining() : 0;
		}

		return 0;
	}

	@Override
	public boolean hasRemaining() {
		Object p = promise;
		return !(p instanceof EOS) && !(p instanceof Closed);
	}

	@Override
	public void endOfStream() {
		for (Object p = promise; ; p = promise) {
			if (p instanceof Closed) return;
			if (!REF.compareAndSet(this, p, EOS.instance)) continue;

			if (p == null) {
				return;
			} else if (p instanceof ReadPromise) {
				supplyEos((ReadPromise) p);
				return;
			} else if (p instanceof WritePromise) {
				((WritePromise) p).cancel();
				return;
			}
		}
	}

	@Override
	public void close() {
		close(new IOException("Pipe closed"));
	}

	public void close(Throwable ex) {
		Closed c = null;

		for (Object p = promise; ; p = promise) {
			if (p instanceof Closed) return;
			if (c == null) c = new Closed(ex);
			if (REF.compareAndSet(this, p, c)) {
				if (p instanceof Promise) ((Promise<?>) p).completeExceptionally(ex);
				break;
			}
		}
	}

	@Override
	public boolean isAsync() {
		return true;
	}

	private Object getLock() {
		return this;
	}

	private void supplyEos(ReadPromise rp) {
		if (rp.bbs == null) {
			rp.complete(emptyByteBuffer());
		} else {
			ByteBuffer bb = rp.bbs.getByteBuffer();
			bb.limit(bb.position());
			rp.complete(bb);
		}
	}

	private class AsyncPipePromise<T> extends Promise<T> {

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (super.cancel(mayInterruptIfRunning)) {
				AsyncPipe.this.close(getFailure());
				return true;
			} else {
				return false;
			}
		}

		@Override
		public boolean completeExceptionally(@NonNull Throwable ex) {
			if (super.completeExceptionally(ex)) {
				AsyncPipe.this.close(ex);
				return true;
			} else {
				return false;
			}
		}
	}

	private final class ReadPromise extends AsyncPipePromise<ByteBuffer> {
		final ByteBufferSupplier bbs;

		ReadPromise() {
			this(null);
		}

		ReadPromise(ByteBufferSupplier bbs) {
			this.bbs = bbs;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (super.cancel(mayInterruptIfRunning)) {
				AsyncPipe.this.close(getFailure());
				return true;
			}
			return false;
		}
	}

	private final class WritePromise extends AsyncPipePromise<Void> {
		final ByteBuffer buff;

		public WritePromise(ByteBuffer buff) {
			this.buff = buff;
		}
	}

	private static final class EOS {
		@SuppressWarnings("InstantiationOfUtilityClass")
		static final EOS instance = new EOS();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final class Closed {
		final FutureSupplier fail;

		Closed(Throwable ex) {
			fail = failed(ex);
		}

		<T> FutureSupplier<T> get() {
			return fail;
		}
	}
}
