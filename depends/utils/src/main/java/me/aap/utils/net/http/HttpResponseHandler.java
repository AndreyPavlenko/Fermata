package me.aap.utils.net.http;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.NetChannel;

/**
 * @author Andrey Pavlenko
 */
public interface HttpResponseHandler {

	FutureSupplier<?> handleResponse(HttpResponse resp);

	default void onFailure(NetChannel channel, Throwable fail) {
		channel.close();
		Log.d(fail, "Failed to receive HTTP response");
	}
}
