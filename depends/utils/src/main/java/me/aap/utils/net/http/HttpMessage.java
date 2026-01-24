package me.aap.utils.net.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BiFunction;
import me.aap.utils.io.AsyncInputStream;
import me.aap.utils.io.AsyncOutputStream;
import me.aap.utils.io.AsyncPipe;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.net.NetChannel;
import me.aap.utils.text.TextUtils;

import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public interface HttpMessage {
	int MAX_PAYLOAD_LEN = MemOutputStream.MAX_SIZE;

	@NonNull
	NetChannel getChannel();

	@NonNull
	HttpVersion getVersion();

	@NonNull
	CharSequence getHeaders();

	@Nullable
	CharSequence getContentType();

	@Nullable
	CharSequence getContentEncoding();

	@Nullable
	CharSequence getTransferEncoding();

	long getContentLength();

	boolean isConnectionClose();

	@Nullable
	default CharSequence getCharset() {
		CharSequence ct = getContentType();
		if (ct == null) return null;
		int idx = TextUtils.indexOf(ct, "charset=");
		return (idx == -1) ? null : TextUtils.trim(ct.subSequence(idx + 8, ct.length()));
	}

	default <T> FutureSupplier<T> getPayload(BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer) {
		return getPayload(consumer, true);
	}

	default <T> FutureSupplier<T> getPayload(BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer, boolean decode) {
		return getPayload(consumer, decode, MAX_PAYLOAD_LEN);
	}

	<T> FutureSupplier<T> getPayload(BiFunction<ByteBuffer, Throwable, FutureSupplier<T>> consumer, boolean decode, int maxLen);

	default FutureSupplier<?> writePayload(File dest) {
		try {
			OutputStream out = new FileOutputStream(dest);
			return writePayload(out).thenRun(() -> IoUtils.close(out));
		} catch (FileNotFoundException ex) {
			return failed(ex);
		}
	}

	default FutureSupplier<?> writePayload(OutputStream out) {
		return writePayload(AsyncOutputStream.from(out));
	}

	FutureSupplier<?> writePayload(AsyncOutputStream out);

	default AsyncInputStream readPayload() {
		AsyncPipe pipe = new AsyncPipe(true);
		writePayload(pipe).onFailure(pipe::close);
		return pipe;
	}

	FutureSupplier<?> skipPayload();
}
