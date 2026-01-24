package me.aap.utils.net.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import me.aap.utils.function.CheckedConsumer;
import me.aap.utils.function.Function;
import me.aap.utils.net.ByteBufferArraySupplier;

/**
 * @author Andrey Pavlenko
 */
public interface HttpResponseBuilder extends HttpHeaderBuilder {

	static HttpResponseBuilder create() {
		return new HttpMessageBuilder();
	}

	static HttpResponseBuilder create(int initCapacity) {
		return new HttpMessageBuilder(initCapacity);
	}

	static ByteBufferArraySupplier supplier(Function<HttpResponseBuilder, ByteBuffer[]> builder) {
		return HttpMessageBuilder.supplier(builder);
	}

	 HttpMessageBuilder setStatusOk(HttpVersion version);

	 HttpMessageBuilder setStatusPartial(HttpVersion version);

	 HttpMessageBuilder setStatus(HttpVersion version, CharSequence status);

	ByteBuffer[] build();

	ByteBuffer[] build(ByteBuffer payload);

	<E extends Throwable> ByteBuffer[] build(CheckedConsumer<OutputStream, E> payloadWriter) throws E;
}
