package me.aap.utils.net.http;


import androidx.annotation.Nullable;

import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface HttpRequestHandler {

	FutureSupplier<?> handleRequest(HttpRequest req);

	interface Provider {
		@Nullable
		HttpRequestHandler getHandler(CharSequence path, HttpMethod method, HttpVersion version);
	}
}
