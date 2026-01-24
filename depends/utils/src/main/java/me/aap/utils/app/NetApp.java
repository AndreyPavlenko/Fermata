package me.aap.utils.app;

import android.annotation.SuppressLint;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.concurrent.ThreadPool;
import me.aap.utils.net.NetHandler;

/**
 * @author Andrey Pavlenko
 */
public class NetApp extends App {
	@SuppressLint("StaticFieldLeak")
	private volatile NetHandler netHandler;

	public static NetApp get() {
		return App.get();
	}


	@Override
	public void onTerminate() {
		NetHandler net = netHandler;
		if (net != null) net.close();
		super.onTerminate();
	}

	public NetHandler getNetHandler() {
		NetHandler net = netHandler;

		if (net == null) {
			synchronized (this) {
				if ((net = netHandler) == null) {
					try {
						netHandler = net = NetHandler.create(o -> {
							o.executor = getExecutor();
							o.scheduler = getScheduler();
							o.inactivityTimeout = getChannelInactivityTimeout();
						});
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				}
			}
		}

		return net;
	}

	protected int getChannelInactivityTimeout() {
		return 5 * 60;
	}

	protected ThreadPool createExecutor() {
		return new NetThreadPool(getNumberOfCoreThreads(), getMaxNumberOfThreads(), 60L, TimeUnit.SECONDS);
	}
}
