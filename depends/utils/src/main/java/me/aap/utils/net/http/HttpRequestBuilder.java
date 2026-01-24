package me.aap.utils.net.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import me.aap.utils.function.CheckedConsumer;
import me.aap.utils.function.Function;
import me.aap.utils.net.ByteBufferArraySupplier;

/**
 * @author Andrey Pavlenko
 */
public interface HttpRequestBuilder extends HttpHeaderBuilder {

	static HttpRequestBuilder create() {
		return new HttpMessageBuilder();
	}

	static HttpRequestBuilder create(int initCapacity) {
		return new HttpMessageBuilder(initCapacity);
	}

	static ByteBufferArraySupplier supplier(Function<HttpRequestBuilder, ByteBuffer[]> builder) {
		return HttpMessageBuilder.supplier(builder);
	}

	HttpMessageBuilder setRequest(CharSequence uri);

	HttpMessageBuilder setRequest(CharSequence uri, HttpMethod m);

	HttpMessageBuilder setRequest(CharSequence uri, HttpMethod m, HttpVersion version);

	ByteBuffer[] build();

	ByteBuffer[] build(ByteBuffer payload);

	<E extends Throwable> ByteBuffer[] build(CheckedConsumer<OutputStream, E> payloadWriter) throws E;
}
