package me.aap.utils.concurrent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Socket;
import java.nio.ByteBuffer;

import me.aap.utils.BuildConfig;
import me.aap.utils.log.Log;
import me.aap.utils.security.SecurityUtils;

import static me.aap.utils.misc.Assert.assertSame;

/**
 * @author Andrey Pavlenko
 */
public class NetThread extends PooledThread {
	public static final int READ_BUFFER_SIZE;
	public static final int WRITE_BUFFER_SIZE;
	public static final int SSL_READ_BUFFER_SIZE;
	public static final int SSL_WRITE_BUFFER_SIZE;
	private ByteBuffer readBuffer;
	private ByteBuffer writeBuffer;
	private ByteBuffer sslReadBuffer;
	private ByteBuffer sslWriteBuffer;

	static {
		int r = 4096;
		int w = 4096;
		int sr = 16921;
		int sw = 16921;

		try (Socket s = new Socket()) {
			r = s.getReceiveBufferSize();
			w = s.getSendBufferSize();
		} catch (Throwable ex) {
			Log.e(ex, "Failed to get socket buffer size");
		}

		try {
			int s = SecurityUtils.createClientSslEngine("localhost", 80).getSession().getPacketBufferSize();
			sr = Math.max(s, (r / s) * s);
			sw = Math.max(s, (w / s) * s);
		} catch (Throwable ex) {
			Log.e(ex, "Failed to get SSL buffer size");
		}

		READ_BUFFER_SIZE = r;
		WRITE_BUFFER_SIZE = w;
		SSL_READ_BUFFER_SIZE = sr;
		SSL_WRITE_BUFFER_SIZE = sw;
		Log.d("Read buffer size: ", r);
		Log.d("Write buffer size: ", w);
		Log.d("SSL read buffer size: ", sr);
		Log.d("SSL write buffer size: ", sw);
	}

	public NetThread() {
	}

	public NetThread(@Nullable Runnable target) {
		super(target);
	}

	public NetThread(@Nullable Runnable target, @NonNull String name) {
		super(target, name);
	}

	public static ByteBuffer getReadBuffer() {
		Thread t = Thread.currentThread();

		if (t instanceof NetThread) {
			NetThread nt = (NetThread) t;

			if (nt.readBuffer != null) {
				nt.readBuffer.clear();
				return nt.readBuffer;
			} else {
				return nt.readBuffer = nt.createReadBuffer();
			}
		}

		Log.w("Not a NetThread: ", t);
		return ByteBuffer.allocate(READ_BUFFER_SIZE);
	}

	public static boolean isReadBuffer(ByteBuffer bb) {
		Thread t = Thread.currentThread();
		return (t instanceof NetThread) && (((NetThread) t).readBuffer == bb);
	}

	public static ByteBuffer getWriteBuffer() {
		Thread t = Thread.currentThread();

		if (t instanceof NetThread) {
			NetThread nt = (NetThread) t;

			if (nt.writeBuffer != null) {
				nt.writeBuffer.clear();
				return nt.writeBuffer;
			} else {
				return nt.writeBuffer = nt.createWriteBuffer();
			}
		}

		Log.w("Not a NetThread: ", t);
		return ByteBuffer.allocate(WRITE_BUFFER_SIZE);
	}

	public static boolean isWriteBuffer(ByteBuffer bb) {
		Thread t = Thread.currentThread();
		return (t instanceof NetThread) && (((NetThread) t).writeBuffer == bb);
	}

	public static ByteBuffer getSslReadBuffer() {
		Thread t = Thread.currentThread();

		if (t instanceof NetThread) {
			NetThread nt = (NetThread) t;

			if (nt.sslReadBuffer != null) {
				nt.sslReadBuffer.clear();
				return nt.sslReadBuffer;
			} else {
				return nt.sslReadBuffer = nt.createSslReadBuffer();
			}
		}

		Log.w("Not a NetThread: ", t);
		return ByteBuffer.allocate(SSL_READ_BUFFER_SIZE);
	}

	public static boolean isSslReadBuffer(ByteBuffer bb) {
		Thread t = Thread.currentThread();
		return (t instanceof NetThread) && (((NetThread) t).sslReadBuffer == bb);
	}

	public static ByteBuffer getSslWriteBuffer() {
		Thread t = Thread.currentThread();

		if (t instanceof NetThread) {
			NetThread nt = (NetThread) t;

			if (nt.sslWriteBuffer != null) {
				nt.sslWriteBuffer.clear();
				return nt.sslWriteBuffer;
			} else {
				return nt.sslWriteBuffer = nt.createSslWriteBuffer();
			}
		}

		Log.w("Not a NetThread: ", t);
		return ByteBuffer.allocate(SSL_WRITE_BUFFER_SIZE);
	}

	public static boolean isSslWriteBuffer(ByteBuffer bb) {
		Thread t = Thread.currentThread();
		return (t instanceof NetThread) && (((NetThread) t).sslWriteBuffer == bb);
	}

	public static void assertReadBuffer(ByteBuffer bb) {
		if (BuildConfig.D) assertSame(((NetThread) Thread.currentThread()).readBuffer, bb);
	}

	public static void assertWriteBuffer(ByteBuffer bb) {
		if (BuildConfig.D) assertSame(((NetThread) Thread.currentThread()).writeBuffer, bb);
	}

	public static void assertSslReadBuffer(ByteBuffer bb) {
		if (BuildConfig.D) assertSame(((NetThread) Thread.currentThread()).sslReadBuffer, bb);
	}

	public static void assertSslWriteBuffer(ByteBuffer bb) {
		if (BuildConfig.D) assertSame(((NetThread) Thread.currentThread()).sslWriteBuffer, bb);
	}

	protected ByteBuffer createReadBuffer() {
		return ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
	}

	protected ByteBuffer createWriteBuffer() {
		return ByteBuffer.allocateDirect(WRITE_BUFFER_SIZE);
	}

	protected ByteBuffer createSslReadBuffer() {
		return ByteBuffer.allocateDirect(SSL_READ_BUFFER_SIZE);
	}

	protected ByteBuffer createSslWriteBuffer() {
		return ByteBuffer.allocateDirect(SSL_WRITE_BUFFER_SIZE);
	}
}
