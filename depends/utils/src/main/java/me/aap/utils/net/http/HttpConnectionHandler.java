package me.aap.utils.net.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.utils.net.NetChannel;
import me.aap.utils.net.NetServer;

/**
 * @author Andrey Pavlenko
 */
public class HttpConnectionHandler extends HttpRequestEncoder implements NetServer.ConnectionHandler {
	private final Map<CharSequence, HttpRequestHandler.Provider> handlers = new ConcurrentHashMap<>();

	public HttpRequestHandler.Provider addHandler(String path, HttpRequestHandler.Provider provider) {
		return handlers.put(path, provider);
	}

	public HttpRequestHandler.Provider removeHandler(String path) {
		return handlers.remove(path);
	}

	@Override
	public void acceptConnection(NetChannel channel) {
		readMessage(channel);
	}

	@Override
	protected HttpRequestHandler getHandler(CharSequence path, HttpMethod method, HttpVersion version) {
		HttpRequestHandler.Provider p = handlers.get(path);
		return (p != null) ? p.getHandler(path, method, version) : null;
	}
}
