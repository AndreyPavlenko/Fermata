package me.aap.utils.net;

import javax.net.ssl.SSLEngine;

import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface SslChannel extends NetChannel {

	static FutureSupplier<? extends SslChannel> create(NetChannel channel, SSLEngine engine) {
		return SslChannelImpl.create(channel, engine);
	}
}
