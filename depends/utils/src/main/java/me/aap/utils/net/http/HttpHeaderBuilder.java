package me.aap.utils.net.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import me.aap.utils.function.CheckedConsumer;

/**
 * @author Andrey Pavlenko
 */
public interface HttpHeaderBuilder {

	HttpMessageBuilder addHeader(HttpHeader h);

	HttpMessageBuilder addHeader(HttpHeader h, long value);

	HttpMessageBuilder addHeader(CharSequence name, long value);

	HttpMessageBuilder addHeader(HttpHeader h, CharSequence value);

	HttpMessageBuilder addHeader(CharSequence name, CharSequence value);

	HttpMessageBuilder addHeader(CharSequence line);
}
