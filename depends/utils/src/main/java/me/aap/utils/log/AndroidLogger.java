package me.aap.utils.log;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import me.aap.utils.BuildConfig;
import me.aap.utils.app.App;
import me.aap.utils.io.IoUtils;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Andrey Pavlenko
 */
public class AndroidLogger extends Logger {
	private final String tag;
	private final File logFile;
	private Msg queue;

	public AndroidLogger() {
		App app = App.get();
		tag = app.getLogTag();
		logFile = app.getLogFile();
	}

	@Override
	public void logDebug(StringBuilder msg) {
		android.util.Log.d(tag, fileLog(msg, null));
	}

	@Override
	public void logDebug(StringBuilder msg, Throwable ex) {
		android.util.Log.d(tag, fileLog(msg, ex), ex);
	}

	@Override
	public void logInfo(StringBuilder msg) {
		android.util.Log.i(tag, fileLog(msg, null));
	}

	@Override
	public void logInfo(StringBuilder msg, Throwable ex) {
		android.util.Log.i(tag, fileLog(msg, ex), ex);
	}

	@Override
	public void logWarn(StringBuilder msg) {
		android.util.Log.w(tag, fileLog(msg, null));
	}

	@Override
	public void logWarn(StringBuilder msg, Throwable ex) {
		android.util.Log.w(tag, fileLog(msg, ex), ex);
	}

	@Override
	public void logError(StringBuilder msg) {
		android.util.Log.e(tag, fileLog(msg, null));
	}

	@Override
	public void logError(StringBuilder msg, Throwable ex) {
		android.util.Log.e(tag, fileLog(msg, ex), ex);
	}

	@Override
	public boolean isLoggable(Log.Level level) {
		int l;

		switch (level) {
			case DEBUG:
				l = android.util.Log.DEBUG;
				break;
			case INFO:
				l = android.util.Log.INFO;
				break;
			case WARN:
				l = android.util.Log.WARN;
				break;
			default:
				l = android.util.Log.ERROR;
		}

		return android.util.Log.isLoggable(tag, l);
	}

	protected int getStackTraceOffset() {
		return 4;
	}

	@Override
	protected boolean addTimeStamp() {
		return (logFile != null);
	}

	@Override
	protected boolean addLevelName() {
		return (logFile != null);
	}

	@Override
	protected boolean addThreadName() {
		return BuildConfig.D || (logFile != null);
	}

	@Override
	protected boolean addCallerLocation() {
		return BuildConfig.D || (logFile != null);
	}

	@Override
	protected boolean addCallerName() {
		return BuildConfig.D || (logFile != null);
	}

	private String fileLog(StringBuilder msg, Throwable ex) {
		String m = msg.toString();
		if (logFile == null) return m;

		synchronized (this) {
			Msg q = queue;
			queue = new Msg(m, ex, q);

			if (q == null) {
				App app = App.get();
				app.getScheduler().schedule(this::flush, app.getLogFlushDelay(), SECONDS);
			}
		}

		return m;
	}

	private void flush() {
		LogWriter w = null;

		try {
			w = new LogWriter(logFile, true);

			Msg msg;

			synchronized (this) {
				msg = queue.head;
			}

			for (long threshold = App.get().getLogRotateThreshold(); ; ) {
				if (w.len >= threshold) {
					w.close();
					File old = new File(logFile.getAbsolutePath() + ".old");
					//noinspection ResultOfMethodCallIgnored
					old.delete();
					//noinspection ResultOfMethodCallIgnored
					logFile.renameTo(old);
					w = new LogWriter(logFile, false);
				}

				w.println(msg.msg);
				if (msg.ex != null) msg.ex.printStackTrace(w);

				synchronized (this) {
					msg = msg.next;

					if (msg == null) {
						queue = null;
						break;
					} else {
						queue.head = msg;
					}
				}
			}

		} catch (Exception ex) {
			android.util.Log.e(tag, "Failed to save log messages in file " + logFile, ex);

			synchronized (this) {
				queue = null;
			}
		} finally {
			IoUtils.close(w);
		}
	}

	private static final class Msg {
		final String msg;
		final Throwable ex;
		Msg head;
		Msg next;

		Msg(String msg, Throwable ex, Msg queue) {
			this.msg = msg;
			this.ex = ex;

			if (queue == null) {
				head = this;
			} else {
				queue.next = this;
				head = queue.head;
			}
		}
	}

	private static final class LogWriter extends PrintWriter {
		long len;

		public LogWriter(File f, boolean append) throws FileNotFoundException {
			super(new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(f, append), StandardCharsets.UTF_8)));
			len = f.length();
		}

		@Override
		public void write(@NonNull String s, int off, int len) {
			super.write(s, off, len);
			this.len += len;
		}

		@Override
		public void write(@NonNull char[] buf, int off, int len) {
			super.write(buf, off, len);
			this.len += len;
		}

		@Override
		public void write(int c) {
			super.write(c);
			this.len += 1;
		}
	}
}
