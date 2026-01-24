package me.aap.utils.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

import me.aap.utils.BuildConfig;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.ConcurrentQueueBase;
import me.aap.utils.function.ProgressiveResultConsumer.Completion;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;

import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.concurrent.NetThread.assertSslReadBuffer;
import static me.aap.utils.concurrent.NetThread.assertSslWriteBuffer;
import static me.aap.utils.concurrent.NetThread.getSslReadBuffer;
import static me.aap.utils.concurrent.NetThread.getSslWriteBuffer;
import static me.aap.utils.io.IoUtils.copyOfRange;
import static me.aap.utils.io.IoUtils.emptyByteBufferArray;

/**
 * @author Andrey Pavlenko
 */
class SslChannelImpl extends ConcurrentQueueBase<SslChannelImpl.Write, SslChannelImpl.Write> implements SslChannel {
	private static final AtomicIntegerFieldUpdater<SslChannelImpl> READ_STATE = AtomicIntegerFieldUpdater.newUpdater(SslChannelImpl.class, "readState");
	private static final ByteBuffer[] EMPTY_BUF_ARRAY = new ByteBuffer[0];
	private final ByteBuffer[] writeBufArray = new ByteBuffer[1];
	private final NetChannel channel;
	private final SSLEngine engine;
	private ByteBuffer tmpBuf;
	private ByteBuffer retainedReadBuf;
	private ByteBuffer retainedWriteBuf;
	// 0 - idle, 1 - reading, 2 - supplying
	private volatile int readState;

	SslChannelImpl(NetChannel channel, SSLEngine engine) {
		this.channel = channel;
		this.engine = engine;
	}

	static FutureSupplier<SslChannelImpl> create(NetChannel channel, SSLEngine engine) {
		try {
			engine.beginHandshake();
		} catch (SSLException ex) {
			return failed(ex);
		}

		return new SslChannelImpl(channel, engine).handshake();
	}

	Handshake handshake() {
		Handshake hs = new Handshake();
		hs.handshake();
		return hs;
	}

	public NetChannel getChannel() {
		return channel;
	}

	@Override
	public NetHandler getHandler() {
		return getChannel().getHandler();
	}

	@Override
	public FutureSupplier<ByteBuffer> read(ByteBufferSupplier supplier, @Nullable Completion<ByteBuffer> consumer) {
		Read r = new Read(supplier, consumer);
		read(r);
		return r;
	}

	private void read(Read r) {
		for (int s = READ_STATE.get(this); ; s = READ_STATE.get(this)) {
			if (s == 1) {
				IOException err = new IOException("Read pending");
				if (r.consumer != null) r.consumer.accept(null, err);
				r.completeExceptionally(err);
				return;
			} else if (s == 2) {
				// The reader is currently in supplying state. The consumer may call read() again,
				// thus, to avoid stack overflow, attempting to read from a different thread.
				getChannel().getHandler().getExecutor().execute(() -> read(r));
				return;
			} else if (READ_STATE.compareAndSet(this, 0, 1)) {
				r.unwrap();
				return;
			}
		}
	}

	@Override
	public FutureSupplier<Void> write(ByteBufferArraySupplier supplier, @Nullable Completion<Void> consumer) {
		Write w = new Write(supplier);
		if (consumer != null) w.addConsumer(consumer);
		offerNode(w);
		if (peekNode() == w) w.wrap();
		return w;
	}

	@Override
	public boolean isOpen() {
		return getChannel().isOpen();
	}

	@Override
	public void close() {
		Log.d("Closing channel: ", this);
		getChannel().close();
	}

	@Override
	public void setCloseListener(CloseListener listener) {
		getChannel().setCloseListener(listener);
	}

	@Nonnull
	@Override
	public String toString() {
		return "SslChannel: " + getChannel();
	}

	private static int getBufferOffset(ByteBuffer[] buf) {
		for (int i = 0; i < buf.length; i++) {
			if (buf[i].hasRemaining()) {
				return i;
			}
		}
		return -1;
	}

	private class SslPromise<T> extends Promise<T> implements ByteBufferSupplier, ByteBufferArraySupplier {

		SSLEngineResult wrap(ByteBuffer[] src, ByteBuffer dst) throws SSLException {
			assert engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
			SSLEngineResult r = engine.wrap(src, dst);
			if (r.getStatus() != SSLEngineResult.Status.OK) return r;

			for (int off = getBufferOffset(src); off != -1; off = getBufferOffset(src)) {
				if (engine.wrap(src, off, src.length - off, dst).getStatus() != SSLEngineResult.Status.OK)
					return r;
			}

			return r;
		}

		SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
			assert engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
			int srcPos = src.position();
			int dstPos = dst.position();
			SSLEngineResult r = engine.unwrap(src, dst);
			if (r.getStatus() != SSLEngineResult.Status.OK) return r;
			if ((r.bytesProduced() != 0) && !src.hasRemaining()) return r;

			while (src.hasRemaining()) {
				if (engine.unwrap(src, dst).getStatus() != SSLEngineResult.Status.OK) break;
			}

			if (dstPos == dst.position()) {
				return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW,
						HandshakeStatus.NOT_HANDSHAKING, src.position() - srcPos, 0);
			} else {
				return new SSLEngineResult(SSLEngineResult.Status.OK,
						HandshakeStatus.NOT_HANDSHAKING, src.position() - srcPos, dst.position() - dstPos);
			}
		}

		@Override
		public ByteBuffer getByteBuffer() {
			ByteBuffer bb = getSslReadBuffer();

			if (retainedReadBuf != null) {
				bb.put(retainedReadBuf);
				assert !retainedReadBuf.hasRemaining();
				releaseByteBuffer(retainedReadBuf);
			}

			return bb;
		}

		@Override
		public ByteBuffer[] getByteBufferArray() {
			if (retainedWriteBuf == null) return wrapTo(getSslWriteBuffer());
			writeBufArray[0] = retainedWriteBuf;
			return writeBufArray;
		}

		ByteBuffer[] wrapTo(ByteBuffer dst) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ByteBufferSupplier retainByteBuffer(ByteBuffer bb) {
			assert bb.hasRemaining();
			if (bb == retainedReadBuf) return this;

			assert retainedReadBuf == null;
			assertSslReadBuffer(bb);
			retainedReadBuf = IoUtils.copyOf(bb);
			// Log.d("Retaining SSL read buffer ", retainedReadBuf, ". Channel: ", SslChannelImpl.this);
			return this;
		}

		@Override
		public ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
			assert bb == writeBufArray;
			assert fromIndex == 0;
			assert bb[0].hasRemaining();
			if ((retainedWriteBuf != null) && (bb[0] == retainedWriteBuf)) return this;

			assert retainedWriteBuf == null;
			assertSslWriteBuffer(bb[0]);
			retainedWriteBuf = IoUtils.copyOf(bb[0]);
			// Log.d("Retaining SSL write buffer: ", retainedWriteBuf, ". Channel: ", SslChannelImpl.this);
			return this;
		}

		@Override
		public void releaseByteBuffer(ByteBuffer bb) {
			assert !bb.hasRemaining();
			if (bb == retainedReadBuf) {
				// Log.d("Releasing retained SSL read buffer ", bb, ". Channel: ", SslChannelImpl.this);
				retainedReadBuf = null;
			} else if (BuildConfig.D) {
				assertSslReadBuffer(bb);
			}
		}

		@Override
		public void releaseByteBufferArray(ByteBuffer[] bb, int toIndex) {
			assert bb == writeBufArray;
			assert toIndex == 1;
			assert !bb[0].hasRemaining();

			if (bb[0] == retainedWriteBuf) {
				assert !bb[0].hasRemaining();
				// Log.d("Releasing retained SSL write buffer ", bb[0], ". Channel: ", SslChannelImpl.this);
				retainedWriteBuf = null;
			} else if (BuildConfig.D) {
				assertSslWriteBuffer(bb[0]);
			}

			bb[0] = null;
		}

		@Override
		public void release() {
		}

		@NonNull
		@Override
		public String toString() {
			return getClass().getSimpleName() + ": done=" + isDone();
		}
	}

	private final class Handshake extends SslPromise<SslChannelImpl> {
		private final ByteBuffer unwrapBuf = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());

		Handshake() {
		}

		@Override
		public boolean complete(@Nullable SslChannelImpl value) {
			assert unwrapBuf.position() == 0;
			assert unwrapBuf.limit() == unwrapBuf.capacity();
			assert retainedWriteBuf == null;
			return super.complete(value);
		}

		void handshake() {
			switch (engine.getHandshakeStatus()) {
				case NOT_HANDSHAKING:
				case FINISHED:
					complete(SslChannelImpl.this);
					return;
				case NEED_TASK:
					for (Runnable t = engine.getDelegatedTask(); t != null;
							 t = engine.getDelegatedTask()) {
						t.run();
					}

					handshake();
					return;
				case NEED_WRAP:
					wrap();
					return;
				case NEED_UNWRAP:
					unwrap();
					return;
				default:
					completeExceptionally(new SSLException("Unexpected SSL handshake status: " + engine.getHandshakeStatus()));
			}
		}

		private void wrap() {
			getChannel().write(this, (v, err) -> {
				if (err != null) completeExceptionally(err);
				else handshake();
			});
		}

		@Override
		ByteBuffer[] wrapTo(ByteBuffer dst) {
			SSLEngineResult result;

			try {
				result = engine.wrap(EMPTY_BUF_ARRAY, dst);
			} catch (SSLException ex) {
				completeExceptionally(ex);
				return EMPTY_BUF_ARRAY;
			}

			switch (result.getStatus()) {
				case OK:
					dst.flip();

					if (dst.hasRemaining()) {
						writeBufArray[0] = dst;
						return writeBufArray;
					} else {
						assert result.getHandshakeStatus() == HandshakeStatus.FINISHED;
						return emptyByteBufferArray();
					}
				case BUFFER_OVERFLOW:
					completeExceptionally(new BufferOverflowException());
					return EMPTY_BUF_ARRAY;
				case BUFFER_UNDERFLOW:
					completeExceptionally(new BufferUnderflowException());
					return EMPTY_BUF_ARRAY;
				case CLOSED:
					completeExceptionally(SelectorHandler.ChannelClosed.get());
					return EMPTY_BUF_ARRAY;
				default:
					completeExceptionally(new IllegalStateException("Invalid SSL wrap result: " + result));
					return EMPTY_BUF_ARRAY;
			}
		}

		private void unwrap() {
			if (retainedReadBuf != null) unwrap(retainedReadBuf);
			else read();
		}

		private void unwrap(ByteBuffer bb) {
			SSLEngineResult result;

			try {
				result = engine.unwrap(bb, unwrapBuf);
			} catch (SSLException ex) {
				completeExceptionally(ex);
				return;
			}

			switch (result.getStatus()) {
				case OK:
					if (bb.hasRemaining()) retainByteBuffer(bb);
					else releaseByteBuffer(bb);
					handshake();
					return;
				case BUFFER_UNDERFLOW:
					if (bb.hasRemaining()) retainByteBuffer(bb);
					else releaseByteBuffer(bb);
					read();
					return;
				case BUFFER_OVERFLOW:
					completeExceptionally(new BufferOverflowException());
					return;
				case CLOSED:
					completeExceptionally(SelectorHandler.ChannelClosed.get());
					return;
				default:
					completeExceptionally(new IllegalStateException("Unrecognized SSL unwrap result: " + result));
			}
		}

		private void read() {
			getChannel().read(this, (bb, err) -> {
				if (err != null) {
					completeExceptionally(err);
				} else if (bb.hasRemaining()) {
					unwrap(bb);
				} else {
					completeExceptionally(SelectorHandler.ChannelClosed.get());
				}
			});
		}
	}

	private final class Read extends SslPromise<ByteBuffer> {
		final ByteBufferSupplier bbs;
		@Nullable
		final Completion<ByteBuffer> consumer;

		Read(ByteBufferSupplier bbs, @Nullable Completion<ByteBuffer> consumer) {
			this.bbs = bbs;
			this.consumer = consumer;
		}

		void unwrap() {
			if (tmpBuf != null) {
				assert tmpBuf.hasRemaining();
				ByteBuffer dst = bbs.getByteBuffer();
				int limit = tmpBuf.limit();
				int n = Math.min(dst.remaining(), tmpBuf.remaining());
				tmpBuf.limit(tmpBuf.position() + n);
				dst.put(tmpBuf);
				tmpBuf.limit(limit);

				if (!tmpBuf.hasRemaining()) {
					Log.d("Releasing temporary buffer " + tmpBuf, ". Channel: ", SslChannelImpl.this);
					tmpBuf = null;
				}

				dst.flip();
				done(dst, null);
			} else if (retainedReadBuf == null) {
				read();
			} else {
				unwrap(retainedReadBuf);
			}
		}

		private void unwrap(ByteBuffer src) {
			ByteBuffer dst = bbs.getByteBuffer();
			assert src.hasRemaining();
			assert dst.hasRemaining();
			SSLEngineResult result;

			try {
				result = unwrap(src, dst);
				if (result.getStatus() != SSLEngineResult.Status.OK) bbs.releaseByteBuffer(dst);
			} catch (SSLException ex) {
				Log.e(ex, "Failed to unwrap from ", src, ". Channel: ", SslChannelImpl.this);
				releaseByteBuffer(src);
				bbs.releaseByteBuffer(dst);
				done(null, ex);
				return;
			}

			switch (result.getStatus()) {
				case OK:
					assert result.bytesProduced() != 0;
					dst.flip();
					assert dst.hasRemaining();
					if (src.hasRemaining()) retainByteBuffer(src);
					else releaseByteBuffer(src);
					done(dst, null);
					return;
				case BUFFER_OVERFLOW:
					bbs.releaseByteBuffer(dst);
					unwrapToTmpBuffer(src);
					return;
				case BUFFER_UNDERFLOW:
					bbs.releaseByteBuffer(dst);
					if (src.hasRemaining()) retainByteBuffer(src);
					else releaseByteBuffer(src);
					read();
					return;
				case CLOSED:
					// FIXME
					dst.limit(dst.position());
					done(dst, null);
					return;
				default:
					bbs.releaseByteBuffer(dst);
					done(null, new IllegalStateException("Unrecognized SSL unwrap result: " + result));
			}
		}

		private void unwrapToTmpBuffer(ByteBuffer src) {
			assert src.hasRemaining();
			SSLEngineResult result;

			try {
				assert tmpBuf == null;
				tmpBuf = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
				Log.d("Unwrapping to temporary buffer " + tmpBuf, ". Channel: ", SslChannelImpl.this);
				result = unwrap(src, tmpBuf);
				if (result.getStatus() != SSLEngineResult.Status.OK) {
					Log.d("Releasing temporary buffer " + tmpBuf, ". Channel: ", SslChannelImpl.this);
					tmpBuf = null;
				}
			} catch (SSLException ex) {
				Log.e(ex, "Failed to unwrap from ", src, ". Channel: ", SslChannelImpl.this);
				tmpBuf = null;
				releaseByteBuffer(src);
				done(null, ex);
				return;
			}

			switch (result.getStatus()) {
				case OK:
					assert result.bytesProduced() != 0;
					tmpBuf.flip();
					assert tmpBuf.hasRemaining();
					if (src.hasRemaining()) retainByteBuffer(src);
					else releaseByteBuffer(src);
					unwrap();
					return;
				case BUFFER_OVERFLOW:
					done(null, new BufferOverflowException());
					return;
				case BUFFER_UNDERFLOW:
					if (src.hasRemaining()) retainByteBuffer(src);
					else releaseByteBuffer(src);
					read();
					return;
				case CLOSED:
					// FIXME
					ByteBuffer dst = bbs.getByteBuffer();
					dst.limit(dst.position());
					done(dst, null);
					return;
				default:
					done(null, new IllegalStateException("Unrecognized SSL unwrap result: " + result));
			}
		}

		private void read() {
			getChannel().read(this, (bb, err) -> {
				if (err != null) {
					done(null, err);
				} else if (!bb.hasRemaining()) {
					ByteBuffer dst = bbs.getByteBuffer();
					dst.limit(dst.position());
					done(dst, null);
				} else {
					unwrap(bb);
				}
			});
		}

		private void done(ByteBuffer result, Throwable fail) {
			assert readState == 1 : "Unexpected readState: " + readState;
			bbs.release();

			try {
				readState = 2;
				if (consumer != null) consumer.accept(result, fail);
				if (fail != null) completeExceptionally(fail);
				else complete(result);
			} finally {
				assert readState == 2;
				readState = 0;
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater NEXT = AtomicReferenceFieldUpdater.newUpdater(Write.class, Write.class, "next");
	private static final AtomicIntegerFieldUpdater<Write> STATE = AtomicIntegerFieldUpdater.newUpdater(Write.class, "state");

	final class Write extends SslPromise<Void> implements ConcurrentQueueBase.Node<Write> {
		private ByteBufferArraySupplier bbs;
		volatile int state;
		volatile Write next;

		Write(ByteBufferArraySupplier bbs) {
			this.bbs = bbs;
		}

		void wrap() {
			if (STATE.compareAndSet(this, 0, 1)) write();
		}

		private void write() {
			getChannel().write(this, (v, err) -> {
				if (err != null) completeExceptionally(err);
				else if (state == 2) done(null);
				else write();
			});
		}

		@Override
		ByteBuffer[] wrapTo(ByteBuffer dst) {
			assert peekNode() == this;
			ByteBuffer[] src = bbs.getByteBufferArray();
			SSLEngineResult result;

			try {
				assert getBufferOffset(src) != -1;
				assert dst.hasRemaining();
				result = wrap(src, dst);
				if (result.getStatus() != SSLEngineResult.Status.OK) bbs.releaseByteBufferArray(src);
			} catch (SSLException ex) {
				bbs.releaseByteBufferArray(src);
				done(ex);
				return EMPTY_BUF_ARRAY;
			}

			switch (result.getStatus()) {
				case OK:
					assert result.bytesConsumed() != 0;
					assert result.bytesProduced() != 0;
					dst.flip();
					assert dst.hasRemaining();
					int i = getBufferOffset(src);

					if (i == -1) {
						state = 2;
						bbs.releaseByteBufferArray(src);
					} else {
						if (i != 0) bbs.releaseByteBufferArray(src, i);
						bbs = bbs.retainByteBufferArray(src, i);
					}

					writeBufArray[0] = dst;
					return writeBufArray;
				case BUFFER_OVERFLOW:
					done(new BufferOverflowException());
					return EMPTY_BUF_ARRAY;
				case BUFFER_UNDERFLOW:
					done(new BufferUnderflowException());
					return EMPTY_BUF_ARRAY;
				case CLOSED:
					done(SelectorHandler.ChannelClosed.get());
					return EMPTY_BUF_ARRAY;
				default:
					done(new IllegalStateException("Invalid SSL wrap result: " + result));
					return EMPTY_BUF_ARRAY;
			}
		}

		private void done(Throwable fail) {
			if (fail != null) {
				if (!completeExceptionally(fail)) return;
			} else {
				assert retainedWriteBuf == null;
				if (!complete(null)) return;
			}

			bbs.release();

			Write next = getNext();
			Write w = pollNode();
			assert w == this;
			if (next != null) next.wrap();
		}

		@Override
		public Write getValue() {
			return this;
		}

		@Override
		public Write getNext() {
			return next;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean compareAndSetNext(ConcurrentQueueBase.Node<Write> expect,
																		 ConcurrentQueueBase.Node<Write> update) {
			return NEXT.compareAndSet(this, expect, update);
		}
	}
}
