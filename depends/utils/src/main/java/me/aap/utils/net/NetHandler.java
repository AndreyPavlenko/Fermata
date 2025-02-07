package me.aap.utils.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.net.ssl.SSLEngine;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.concurrent.ConcurrentUtils;
import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.function.BiFunction;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.NetServer.ConnectionHandler;

/**
 * @author Andrey Pavlenko
 */
public interface NetHandler extends Closeable {

	static NetHandler create(Consumer<Opts> builder) throws IOException {
		Opts o = new Opts();
		builder.accept(o);
		return create(o);
	}

	static NetHandler create(Opts opts) throws IOException {
		return new SelectorHandler(opts);
	}

	Executor getExecutor();

	ScheduledExecutorService getScheduler();

	int getInactivityTimeout();

	boolean isOpen();

	@Override
	void close();

	FutureSupplier<NetServer> bind(BindOpts opts);

	default FutureSupplier<NetServer> bind(Consumer<BindOpts> c) {
		BindOpts o = new BindOpts();
		c.accept(o);
		return bind(o);
	}

	FutureSupplier<NetChannel> connect(ConnectOpts opts);

	default FutureSupplier<NetChannel> connect(Consumer<ConnectOpts> c) {
		ConnectOpts o = new ConnectOpts();
		c.accept(o);
		return connect(o);
	}

	class Opts {
		public Executor executor;
		public ScheduledExecutorService scheduler;
		public int inactivityTimeout;

		Executor getExecutor() {
			return (executor == null) ?
					executor = new NetThreadPool(Runtime.getRuntime().availableProcessors()) : executor;
		}

		ScheduledExecutorService getScheduler() {
			return (scheduler == null) ? scheduler = Executors.newScheduledThreadPool(1) : scheduler;
		}
	}

	class ChannelOpts {
		public Map<SocketOption<?>, Object> opt = new HashMap<>();
		public SocketAddress address;
		public String host;
		public int port;
		public boolean ssl;
		public int readTimeout;
		public int writeTimeout;
		public int sendTimeout;

		SocketAddress getAddress() {
			if (address != null) return address;
			if (host == null) return new InetSocketAddress(port);
			if (ConcurrentUtils.isMainThread()) return new InetSocketAddress(host, port);

			try { // Prefer IPv4 addresses
				var all = InetAddress.getAllByName(host);
				for (InetAddress a : all) {
					if (a instanceof Inet4Address) return new InetSocketAddress(a, port);
				}
				if (all.length > 0) return new InetSocketAddress(all[0], port);
			} catch (UnknownHostException err) {
				Log.e(err);
			}

			return new InetSocketAddress(host, port);
		}
	}

	class BindOpts extends ChannelOpts {
		public ConnectionHandler handler;
		public int backlog;
		public Supplier<SSLEngine> sslEngine;
	}

	class ConnectOpts extends ChannelOpts {
		public SocketAddress bindAddress;
		public String bindHost;
		public int bindPort;
		public int connectTimeout;
		public BiFunction<String, Integer, SSLEngine> sslEngine;

		SocketAddress getBindAddress() {
			if (bindAddress != null) {
				return bindAddress;
			} else if (bindHost != null) {
				return new InetSocketAddress(bindHost, bindPort);
			} else if (bindPort != 0) {
				return new InetSocketAddress(bindPort);
			} else {
				return null;
			}
		}
	}
}
