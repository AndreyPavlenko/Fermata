package me.aap.utils.io;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class RandomAccessFileChannelWrapper implements RandomAccessChannel {
	@Nullable
	private final FileChannel readChannel;
	@Nullable
	private final FileChannel writeChannel;
	private final AutoCloseable[] close;

	public RandomAccessFileChannelWrapper(FileChannel ch, AutoCloseable... close) {
		this(ch, ch, close);
	}

	public RandomAccessFileChannelWrapper(@Nullable FileChannel readChannel,
																				@Nullable FileChannel writeChannel,
																				AutoCloseable... close) {
		this.readChannel = readChannel;
		this.writeChannel = writeChannel;
		this.close = close;
	}

	@Override
	public int read(ByteBuffer dst, long position) throws IOException {
		return getReadChannel().read(dst, position);
	}

	@Override
	public int write(ByteBuffer src, long position) throws IOException {
		return getWriteChannel().write(src, position);
	}

	@Override
	public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
		return getWriteChannel().transferFrom(src, position, count);
	}

	@Override
	public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
		return getReadChannel().transferTo(position, count, target);
	}

	@Override
	public long transferFrom(RandomAccessChannel src, long srcPos, long pos, long count) throws IOException {
		if (src instanceof RandomAccessFileChannelWrapper) {
			FileChannel from = ((RandomAccessFileChannelWrapper) src).getReadChannel();
			from.position(srcPos);
			return getWriteChannel().transferFrom(from, pos, count);
		} else {
			return RandomAccessChannel.super.transferFrom(src, srcPos, pos, count);
		}
	}

	@Override
	public long transferTo(long pos, long targetPos, long count, RandomAccessChannel target) throws IOException {
		if (target instanceof RandomAccessFileChannelWrapper) {
			FileChannel to = ((RandomAccessFileChannelWrapper) target).getWriteChannel();
			to.position(targetPos);
			return getWriteChannel().transferTo(pos, count, to);
		} else {
			return RandomAccessChannel.super.transferTo(pos, targetPos, count, target);
		}
	}

	@Override
	public RandomAccessChannel truncate(long size) throws IOException {
		getWriteChannel().truncate(size);
		return this;
	}

	@Override
	public ByteBuffer map(String mode, long pos, long size) throws IOException {
		if (mode.contains("w")) return getWriteChannel().map(FileChannel.MapMode.READ_WRITE, pos, size);
		else return getReadChannel().map(FileChannel.MapMode.READ_ONLY, pos, size);
	}

	@Override
	public long size() {
		try {
			return (readChannel != null) ? readChannel.size() : (writeChannel != null) ? writeChannel.size() : 0;
		} catch (IOException ex) {
			Log.e(ex, "Failed to get channel size");
			return 0;
		}
	}

	public FileChannel getReadChannel() throws NonReadableChannelException {
		if (readChannel == null) throw new NonReadableChannelException();
		return readChannel;
	}

	public FileChannel getWriteChannel() throws NonWritableChannelException {
		if (writeChannel == null) throw new NonWritableChannelException();
		return writeChannel;
	}

	@Override
	public void close() {
		doClose();
	}

	@Override
	protected void finalize() {
		doClose();
	}

	protected void doClose() {
		IoUtils.close(readChannel, writeChannel);
		IoUtils.close(close);
	}
}
