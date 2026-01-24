package me.aap.utils.net;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.misc.Assert.assertEquals;

import android.os.Build;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLEngine;

import me.aap.utils.BuildConfig;
import me.aap.utils.async.Completable;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.async.RunnablePromise;
import me.aap.utils.concurrent.ConcurrentQueueBase;
import me.aap.utils.concurrent.ConcurrentQueueBase.Node;
import me.aap.utils.concurrent.ConcurrentUtils;
import me.aap.utils.concurrent.PooledThread;
import me.aap.utils.function.ProgressiveResultConsumer.Completion;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.log.Log;
import me.aap.utils.security.SecurityUtils;

/**
 * @author Andrey Pavlenko
 */
class SelectorHandler implements NetHandler, Runnable {
	private final Executor executor;
	private final ScheduledExecutorService scheduler;
	private final int inactivityTimeout;
	private final Selector selector;
	private final ScheduledFuture<?> inactiveChannelCleaner;
	private final Thread selectorThread;
	private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

	SelectorHandler(Opts opts) throws IOException {
		executor = opts.getExecutor();
		scheduler = opts.getScheduler();
		selector = Selector.open();

		if (opts.inactivityTimeout > 0) {
			inactivityTimeout = opts.inactivityTimeout * 1000;
			inactiveChannelCleaner = scheduler.scheduleWithFixedDelay(this::cleanInactive,
					inactivityTimeout, inactivityTimeout, MILLISECONDS);
		} else {
			inactivityTimeout = 0;
			inactiveChannelCleaner = null;
		}

		selectorThread = new PooledThread(this, "SelectorHandler");
		selectorThread.setDaemon(true);
		selectorThread.start();
	}

	@Override
	protected void finalize() {
		close();
	}

	@Override
	public void run() {
		while (selector.isOpen()) {
			try {
				selector.select();

				for (Runnable run = queue.poll(); run != null; run = queue.poll()) {
					run.run();
				}

				Set<SelectionKey> keys = selector.selectedKeys();

				for (Iterator<SelectionKey> it = keys.iterator(); it.hasNext(); ) {
					SelectionKey k = it.next();
					it.remove();

					if (k.isValid()) {
						Selectable select = (Selectable) k.attachment();
						if (select != null) select.select();
					}
				}
			} catch (Throwable ex) {
				if (!selector.isOpen()) break;
				Log.e(ex, "Selector failed");
			}
		}
	}

	@Override
	public FutureSupplier<NetServer> bind(BindOpts opts) {
		try {
			ServerSocketChannel channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			SelectableNetServer server = new SelectableNetServer(channel, opts);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				channel.bind(opts.getAddress(), opts.backlog);
			} else {
				channel.socket().bind(opts.getAddress(), opts.backlog);
			}

			RunnablePromise<NetServer> p = new RunnablePromise<NetServer>() {
				@Override
				protected NetServer runTask() throws ClosedChannelException {
					channel.register(selector, OP_ACCEPT, server);
					return server;
				}

				@Nullable
				@Override
				public Executor getExecutor() {
					return SelectorHandler.this.getExecutor();
				}
			};

			selectorRun(p);
			return p;
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	@Override
	public FutureSupplier<NetChannel> connect(ConnectOpts o) {
		try {
			SocketChannel ch = SocketChannel.open();
			Promise<NetChannel> p = new Promise<NetChannel>() {
				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					if (!super.cancel(mayInterruptIfRunning)) return false;
					IoUtils.close(ch);
					return true;
				}

				@Nullable
				@Override
				public Executor getExecutor() {
					return SelectorHandler.this.getExecutor();
				}
			};

			if (ConcurrentUtils.isMainThread()) getExecutor().execute(() -> connect(o, ch, p));
			else connect(o, ch, p);
			return p;
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	private void connect(ConnectOpts o, SocketChannel ch, Promise<NetChannel> p) {
		try {
			SocketAddress addr = o.getAddress();
			SocketAddress bindAddr = o.getBindAddress();
			ch.configureBlocking(false);
			setOpts(ch, o.opt);

			if (bindAddr != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					ch.bind(bindAddr);
				} else {
					ch.socket().bind(bindAddr);
				}
			}

			ch.connect(addr);
			startTimer(p, o.connectTimeout, Timer.CONNECT);

			selectorRun(() -> {
				try {
					SelectionKey key = ch.register(selector, OP_CONNECT);
					SelectableNetChannel nc = ((o.readTimeout | o.writeTimeout | o.sendTimeout) == 0)
							? new SelectableNetChannel(key)
							: new SelectableNetChannelWitTimeout(key, o.readTimeout, o.writeTimeout, o.sendTimeout);

					key.attach((Selectable) () -> {
						try {
							assertEquals(OP_CONNECT, key.interestOps());
							if (!key.isConnectable() || !ch.finishConnect()) return;
							key.attach(nc);
							key.interestOps(0);

							getExecutor().execute(() -> {
								if (o.ssl) {
									if (o.host == null) o.host = ((InetSocketAddress) addr).getHostString();
									if (o.sslEngine == null) o.sslEngine = SecurityUtils::createClientSslEngine;
									SslChannel.create(nc, o.sslEngine.apply(o.host, o.port)).onCompletionSupply(p);
								} else {
									p.complete(nc);
								}
							});
						} catch (CancelledKeyException ignore) {
						} catch (Throwable ex) {
							getExecutor().execute(() -> p.completeExceptionally(ex));
						}
					});
				} catch (Throwable ex) {
					getExecutor().execute(() -> p.completeExceptionally(ex));
				}
			});
		} catch (Throwable ex) {
			p.completeExceptionally(ex);
		}
	}

	@Override
	public void close() {
		if (!selector.isOpen()) return;
		selectorRun(this::doClose);
		if (!selector.isOpen()) queue.clear();
	}

	private void doClose() {
		for (SelectionKey k : selector.keys()) {
			try {
				Object a = k.attachment();
				if (a instanceof Closeable) ((Closeable) a).close();
				else k.channel().close();
			} catch (Throwable ignore) {
			}
		}

		IoUtils.close(selector);
		if (inactiveChannelCleaner != null) inactiveChannelCleaner.cancel(false);
		queue.clear();
	}

	@Override
	public boolean isOpen() {
		return selector.isOpen();
	}

	@Override
	public Executor getExecutor() {
		return executor;
	}

	@Override
	public ScheduledExecutorService getScheduler() {
		return scheduler;
	}

	@Override
	public int getInactivityTimeout() {
		return inactivityTimeout;
	}

	private void cleanInactive() {
		selectorRun(() -> {
			Set<SelectionKey> keys = selector.keys();
			if (keys.isEmpty()) return;

			long timeout = System.currentTimeMillis() - inactivityTimeout;

			for (SelectionKey k : keys) {
				try {
					Object a = k.attachment();
					if (a instanceof SelectableNetChannel)
						((SelectableNetChannel) a).closeIfInactive(timeout);
				} catch (Throwable ignore) {
				}
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setOpts(SocketChannel ch, Map<SocketOption<?>, ?> opts) throws IOException {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			for (Map.Entry<SocketOption<?>, ?> e : opts.entrySet()) {
				ch.setOption((SocketOption) e.getKey(), e.getValue());
			}
		}
	}

	private static int getBufferOffset(ByteBuffer[] buf) {
		for (int i = 0; i < buf.length; i++) {
			if (buf[i].hasRemaining()) {
				return i;
			}
		}
		return -1;
	}

	private boolean isSelectorThread() {
		return Thread.currentThread() == selectorThread;
	}

	private void selectorRun(Runnable run) {
		if (isSelectorThread()) {
			run.run();
		} else {
			queue.add(run);
			selector.wakeup();
		}
	}

	private void startTimer(Promise<?> task, int timeout, byte type) {
		if ((timeout > 0) && !task.isDone()) {
			Timer t = new Timer(task, type);
			ScheduledFuture<?> f = t.future = getScheduler().schedule(t, timeout, SECONDS);
			task.thenRun(t::cancel);
			if ((t.future == null) && task.isDone()) f.cancel(false);
		}
	}

	private static final class Timer implements Runnable {
		static final byte CONNECT = 0;
		static final byte READ = 1;
		static final byte WRITE = 2;
		static final byte SEND = 3;
		private Completable<?> task;
		private final byte type;
		ScheduledFuture<?> future;

		Timer(Completable<?> task, byte type) {
			this.task = task;
			this.type = type;
		}

		@Override
		public void run() {
			ScheduledFuture<?> f = future;
			Completable<?> t = task;

			if (f != null) {
				f.cancel(false);
				future = null;
			}

			if (t != null) {
				task = null;
				String msg;

				switch (type) {
					case CONNECT:
						msg = "Connect timeout";
						break;
					case READ:
						msg = "Read timeout";
						break;
					case WRITE:
						msg = "Write timeout";
						break;
					default:
						msg = "Send timeout";
				}
				t.completeExceptionally(new TimeoutException(msg));
			}
		}

		void cancel() {
			ScheduledFuture<?> f = future;
			task = null;

			if (f != null) {
				f.cancel(false);
				future = null;
			}
		}
	}

	private interface Selectable {
		void select();
	}

	private final class SelectableNetServer implements NetServer, Selectable {
		private final ServerSocketChannel channel;
		private final Map<SocketOption<?>, ?> opts;
		private final ConnectionHandler handler;
		private final Supplier<SSLEngine> ssl;
		private final int readTimeout;
		private final int writeTimeout;
		private final int sendTimeout;
		private final boolean hasTimeout;

		public SelectableNetServer(ServerSocketChannel channel, BindOpts o) {
			this.channel = channel;
			opts = o.opt.isEmpty() ? Collections.emptyMap() : new HashMap<>(o.opt);
			handler = requireNonNull(o.handler);
			readTimeout = o.readTimeout;
			writeTimeout = o.writeTimeout;
			sendTimeout = o.sendTimeout;
			hasTimeout = ((readTimeout | writeTimeout | sendTimeout) != 0);

			if (o.ssl) {
				ssl = (o.sslEngine != null) ? o.sslEngine : SecurityUtils::createServerSslEngine;
			} else {
				ssl = null;
			}
		}

		@Override
		public NetHandler getHandler() {
			return SelectorHandler.this;
		}

		@Override
		public int getPort() {
			return channel.socket().getLocalPort();
		}

		@Override
		public SocketAddress getBindAddress() {
			return channel.socket().getLocalSocketAddress();
		}

		@Override
		public void select() {
			SelectableNetChannel nc;
			SocketChannel ch = null;

			try {
				ch = channel.accept();
				if (ch == null) return;

				ch.configureBlocking(false);
				setOpts(ch, opts);

				SelectionKey key = ch.register(selector, 0);
				nc = hasTimeout
						? new SelectableNetChannelWitTimeout(key, readTimeout, writeTimeout, sendTimeout)
						: new SelectableNetChannel(key);
				key.attach(nc);
			} catch (CancelledKeyException ignore) {
				return;
			} catch (Throwable ex) {
				IoUtils.close(ch);
				Log.e(ex, "Failed to accept a connection");
				return;
			}

			try {
				getExecutor().execute(() -> {
					if (ssl != null) {
						SslChannel.create(nc, ssl.get()).onCompletion((sslc, err) -> {
							if (err != null) {
								Log.e(err, "Failed to create SSL channel");
								nc.close();
							} else {
								acceptConnection(sslc);
							}
						});
					} else {
						acceptConnection(nc);
					}
				});
			} catch (Throwable ex) {
				Log.e(ex, "Failed to execute connection handler");
				nc.close();
			}
		}

		private void acceptConnection(NetChannel channel) {
			try {
				handler.acceptConnection(channel);
			} catch (Throwable ex) {
				Log.e(ex, "Connection handler failed");
				channel.close();
			}
		}

		@Override
		public boolean isOpen() {
			return channel.isOpen();
		}

		@Override
		public void close() {
			try {
				channel.close();
			} catch (Throwable ex) {
				Log.e(ex, "Failed to close server channel");
			}
		}

		@Override
		public String toString() {
			return channel.toString();
		}
	}

	private static final AtomicReferenceFieldUpdater<SelectableNetChannel, ReadPromise> READER =
			AtomicReferenceFieldUpdater.newUpdater(SelectableNetChannel.class, ReadPromise.class, "reader");
	private static final AtomicIntegerFieldUpdater<SelectableNetChannel> WRITING =
			AtomicIntegerFieldUpdater.newUpdater(SelectableNetChannel.class, "writing");

	private class SelectableNetChannel
			extends ConcurrentQueueBase<ByteBufferArraySupplier, WritePromise>
			implements NetChannel, Selectable {
		private final SelectionKey key;
		@Keep
		volatile ReadPromise reader;
		@Keep
		volatile int writing;
		private long lastActive;
		private CloseListener closeListener;

		public SelectableNetChannel(SelectionKey key) {
			this.key = key;
		}

		@Override
		public void select() {
			markActive();

			try {
				assertEquals(0, key.interestOps() & (OP_ACCEPT | OP_CONNECT));
				int ready = key.readyOps();
				int interest = key.interestOps();

				if (((ready & OP_READ) != 0) && ((interest & OP_READ) != 0)) {
					key.interestOps(interest &= ~OP_READ);
					getExecutor().execute(this::doRead);
				}

				if (((ready & OP_WRITE) != 0) && ((interest & OP_WRITE) != 0)) {
					key.interestOps(interest & ~OP_WRITE);
					if (WRITING.compareAndSet(this, 0, 1)) getExecutor().execute(this::doWrite);
				}
			} catch (CancelledKeyException ignore) {
			} catch (Throwable ex) {
				Log.d(ex, "Selected operation failed - closing channel ", this);
				close();
			}
		}

		@Override
		public FutureSupplier<ByteBuffer> read(ByteBufferSupplier supplier, @Nullable Completion<ByteBuffer> consumer) {
			ReadPromise p = new ReadPromise(supplier);
			if (consumer != null) p.onCompletion(consumer);

			if (!READER.compareAndSet(this, null, p)) {
				for (ReadPromise r = reader; (r != null); r = reader) {
					if (!r.isDone()) {
						p.completeExceptionally(new IOException("Read pending"));
						return p;
					} else if (READER.compareAndSet(this, r, p)) {
						break;
					}
				}
			}

			if (!isOpen()) {
				p.completeExceptionally(ChannelClosed.get());
				READER.compareAndSet(this, p, null);
			} else {
				assertEquals(0, (key.interestOps() & OP_READ));
				setInterest(p, OP_READ);
			}

			startTimer(p, getReadTimeout(), Timer.READ);
			return p;
		}

		private void doRead() {
			ReadPromise p = reader;
			if (p == null) return;

			ByteBufferSupplier bs = p.supplier;

			if (bs == null) {
				READER.compareAndSet(this, p, null);
				return;
			}

			ByteBuffer buf = bs.getByteBuffer();

			try {
				int i = channel().read(buf);

				if ((i != 0) || !buf.hasRemaining()) {
					if (i == -1) buf.limit(buf.position()); // End of stream
					else buf.flip();
					READER.compareAndSet(this, p, null);
					p.complete(buf);
				} else {
					p.releaseBuf(buf);
					setInterest(p, OP_READ);
				}
			} catch (Throwable ex) {
				READER.compareAndSet(this, p, null);
				p.completeExceptionally(ex);
			}
		}

		@Override
		public FutureSupplier<Void> write(ByteBufferArraySupplier supplier, @Nullable Completion<Void> consumer) {
			if (!isOpen()) return failed(ChannelClosed.get());

			WritePromise p = new WritePromise(supplier);
			if (consumer != null) p.addConsumer(consumer);
			offerNode(p);
			if (peekNode() == p) setInterest(p, OP_WRITE);

			startTimer(p, getWriteTimeout(), Timer.WRITE);
			return p;
		}

		@Override
		public FutureSupplier<Void> send(RandomAccessChannel ch, long off, long len,
																		 @Nullable ByteBufferArraySupplier headerSupplier,
																		 @Nullable Completion<Void> consumer) {
			if (!isOpen()) return failed(ChannelClosed.get());

			SendPromise p = new SendPromise(headerSupplier, ch, off, len);
			if (consumer != null) p.addConsumer(consumer);
			offerNode(p);
			if (peekNode() == p) setInterest(p, OP_WRITE);

			startTimer(p, getSendTimeout(), Timer.SEND);
			return p;
		}

		private void doWrite() {
			try {
				for (SocketChannel ch = channel(); ; ) {
					WritePromise p = peekNode();

					for (; p == null; p = peekNode()) {
						assertEquals(1, writing);
						writing = 0;
						if (isEmpty() || !WRITING.compareAndSet(this, 0, 1)) return;
					}

					ByteBufferArraySupplier bs = p.supplier;

					if (bs == null) {
						RandomAccessChannel sch = p.getSendChannel();

						if (sch != null) {
							if (send(p, sch)) {
								assert p == peekNode();
								poll();
								p.complete(null);
								continue;
							} else {
								writing = 0;
								setInterest(p, OP_WRITE);
								return;
							}
						} else {
							assert p == peekNode();
							assert p.isDone();
							poll();
							continue;
						}
					}

					ByteBuffer[] buf = bs.getByteBufferArray();

					if (buf.length == 0) {
						assert p == peekNode();
						poll();
						if (!p.isDone()) p.complete(null);
						continue;
					}

					assert getBufferOffset(buf) == 0;

					for (int off = 0; ; ) {
						long i = ch.write(buf, off, buf.length - off);

						if (i == 0) {
							if (off != 0) p.releaseBuf(buf, off);
							p.retainBuf(buf, off);
							writing = 0;
							setInterest(p, OP_WRITE);
							return;
						}

						off = getBufferOffset(buf);

						if (off == -1) {
							p.releaseBuf(buf, buf.length);
							p.releaseBufSupplier();
							RandomAccessChannel sch = p.getSendChannel();

							if ((sch != null) && !send(p, sch)) {
								writing = 0;
								setInterest(p, OP_WRITE);
								return;
							}

							assert (p == peekNode()) || !isOpen();
							poll();
							p.complete(null);
							break;
						}
					}
				}
			} catch (Throwable ex) {
				close(ex);
				writing = 0;
			}
		}

		private boolean send(WritePromise p, RandomAccessChannel ch) throws IOException {
			long off = p.getSendChannelOff();
			long len = p.getSendChannelLen();
			assert len > 0;
			long n = ch.transferTo(off, len, channel());

			if (n == -1) {
				throw new IOException("Failed to transfer " + len + " bytes at position " + off);
			} else if (n > 0) {
				if ((len -= n) == 0) {
					p.releaseSendChannel();
					return true;
				} else {
					p.setSendChannelLen(len);
					p.setSendChannelOff(off + n);
					return false;
				}
			} else {
				return false;
			}
		}

		private void setInterest(Completable<?> p, int interest) {
			selectorRun(() -> {
				try {
					if (key.isValid()) key.interestOps(key.interestOps() | interest);
				} catch (Throwable ex) {
					p.completeExceptionally(ex);
				}
			});
		}

		@Override
		public NetHandler getHandler() {
			return SelectorHandler.this;
		}

		@Override
		public boolean isOpen() {
			return channel().isOpen();
		}

		@Override
		public void close() {
			close(ChannelClosed.get());
		}

		@Override
		public void setCloseListener(CloseListener listener) {
			closeListener = listener;
		}

		int getReadTimeout() {
			return 0;
		}

		int getWriteTimeout() {
			return 0;
		}

		int getSendTimeout() {
			return 0;
		}

		void markActive() {
			if (inactivityTimeout != 0) lastActive = System.currentTimeMillis();
		}

		void closeIfInactive(long timeout) {
			if (lastActive < timeout) {
				Log.d("Closing channel due to inactivity: ", this);
				close();
			}
		}

		private void close(Throwable err) {
			Log.d("Closing channel ", this);
			if (!isOpen()) return;
			IoUtils.close(channel());

			ReadPromise r = READER.getAndSet(this, null);
			if (r != null) r.completeExceptionally(err);
			clear(w -> w.completeExceptionally(err));

			CloseListener listener = closeListener;

			if (listener != null) {
				if (isSelectorThread()) getExecutor().execute(() -> listener.channelClosed(this));
				else listener.channelClosed(this);
			}

			// Wake up selector to remove the cancelled key
			selector.wakeup();
		}

		@Nonnull
		@Override
		public String toString() {
			return channel().toString();
		}

		private SocketChannel channel() {
			return (SocketChannel) key.channel();
		}
	}

	private final class SelectableNetChannelWitTimeout extends SelectableNetChannel {
		private final int readTimeout;
		private final int writeTimeout;
		private final int sendTimeout;

		SelectableNetChannelWitTimeout(SelectionKey key, int readTimeout, int writeTimeout, int sendTimeout) {
			super(key);
			this.readTimeout = readTimeout;
			this.writeTimeout = writeTimeout;
			this.sendTimeout = sendTimeout;
		}

		@Override
		public int getReadTimeout() {
			return readTimeout;
		}

		@Override
		public int getWriteTimeout() {
			return writeTimeout;
		}

		@Override
		public int getSendTimeout() {
			return sendTimeout;
		}
	}

	private static abstract class ChannelPromise<T> extends Promise<T> {

		abstract void release();

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			release();
			return super.cancel(mayInterruptIfRunning);
		}

		@Override
		public boolean complete(@Nullable T value) {
			release();
			return super.complete(value);
		}

		@Override
		public boolean completeExceptionally(@NonNull Throwable ex) {
			release();
			return super.completeExceptionally(ex);
		}
	}

	private static final class ReadPromise extends ChannelPromise<ByteBuffer> {
		ByteBufferSupplier supplier;

		ReadPromise(ByteBufferSupplier supplier) {
			this.supplier = supplier;
		}

		void release() {
			ByteBufferSupplier s = supplier;

			if (s != null) {
				supplier = null;
				s.release();
			}
		}

		void releaseBuf(ByteBuffer bb) {
			ByteBufferSupplier s = supplier;
			if (s != null) s.releaseByteBuffer(bb);
		}
	}

	private static class WritePromise extends ChannelPromise<Void> implements Node<ByteBufferArraySupplier> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater NEXT = AtomicReferenceFieldUpdater.newUpdater(WritePromise.class, WritePromise.class, "next");
		private volatile WritePromise next;
		ByteBufferArraySupplier supplier;

		WritePromise(ByteBufferArraySupplier supplier) {
			this.supplier = supplier;
		}

		@Override
		public ByteBufferArraySupplier getValue() {
			return supplier;
		}

		@Override
		public WritePromise getNext() {
			return next;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean compareAndSetNext(Node<ByteBufferArraySupplier> expect, Node<ByteBufferArraySupplier> update) {
			return NEXT.compareAndSet(this, expect, update);
		}

		void retainBuf(ByteBuffer[] bb, int off) {
			ByteBufferArraySupplier s = supplier;
			if (s != null) supplier = s.retainByteBufferArray(bb, off);
			assert supplier != null;
		}

		void releaseBuf(ByteBuffer[] bb, int end) {
			ByteBufferArraySupplier s = this.supplier;
			if (s != null) s.releaseByteBufferArray(bb, end);
		}

		void releaseBufSupplier() {
			ByteBufferArraySupplier s = this.supplier;

			if (s != null) {
				supplier = null;
				s.release();
			}
		}

		void release() {
			releaseBufSupplier();
		}

		@Nullable
		RandomAccessChannel getSendChannel() {
			return null;
		}

		void releaseSendChannel() {
		}

		long getSendChannelOff() {
			return 0;
		}

		void setSendChannelOff(long off) {
		}

		long getSendChannelLen() {
			return 0;
		}

		void setSendChannelLen(long len) {
		}
	}

	private static final class SendPromise extends WritePromise {
		private RandomAccessChannel channel;
		private long off;
		private long len;

		public SendPromise(ByteBufferArraySupplier supplier, RandomAccessChannel channel, long off, long len) {
			super(supplier);
			this.channel = channel;
			this.off = off;
			this.len = len;
		}

		@Nullable
		@Override
		RandomAccessChannel getSendChannel() {
			return channel;
		}

		@Override
		void releaseSendChannel() {
			channel = null;
		}

		@Override
		long getSendChannelOff() {
			return off;
		}

		@Override
		void setSendChannelOff(long off) {
			this.off = off;
		}

		@Override
		long getSendChannelLen() {
			return len;
		}

		@Override
		void setSendChannelLen(long len) {
			this.len = len;
		}

		@Override
		void release() {
			super.release();
			releaseSendChannel();
		}
	}

	static final class ChannelClosed extends ClosedChannelException {
		private static final ChannelClosed instance = new ChannelClosed();

		public static ChannelClosed get() {
			return (BuildConfig.D) ? new ChannelClosed() : instance;
		}

		@NonNull
		@Override
		public String getMessage() {
			return "Channel closed";
		}
	}
}
