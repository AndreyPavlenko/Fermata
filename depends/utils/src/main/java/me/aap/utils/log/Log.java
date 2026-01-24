package me.aap.utils.log;

import me.aap.utils.BuildConfig;
import me.aap.utils.text.SharedTextBuilder;

import static me.aap.utils.log.Log.Level.DEBUG;
import static me.aap.utils.log.Log.Level.ERROR;
import static me.aap.utils.log.Log.Level.INFO;
import static me.aap.utils.log.Log.Level.WARN;
import static me.aap.utils.os.OsUtils.isAndroid;

/**
 * @author Andrey Pavlenko
 */
public abstract class Log {
	private static final Logger impl = isAndroid() ? new AndroidLogger() : new PrintStreamLogger(System.out);

	public static void d(Object... msg) {
		if (!BuildConfig.D) return;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(DEBUG, sb, msg);
			impl.logDebug(sb);
		}
	}

	public static void d(Throwable err, Object... msg) {
		if (!BuildConfig.D) return;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(DEBUG, sb, msg);
			impl.logDebug(sb, err);
		}
	}

	public static void i(Object... msg) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(INFO, sb, msg);
			impl.logInfo(sb);
		}
	}

	public static void i(Throwable err, Object... msg) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(INFO, sb, msg);
			impl.logInfo(sb, err);
		}
	}

	public static void w(Object... msg) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(WARN, sb, msg);
			impl.logWarn(sb);
		}
	}

	public static void w(Throwable err, Object... msg) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(WARN, sb, msg);
			impl.logWarn(sb, err);
		}
	}

	public static void e(Object... msg) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(ERROR, sb, msg);
			impl.logError(sb);
		}
	}

	public static void e(Throwable err, Object... msg) {
		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			StringBuilder sb = tb.getStringBuilder();
			impl.formatMessage(ERROR, sb, msg);
			impl.logError(sb, err);
		}
	}

	public static boolean isLoggableD() {
		return BuildConfig.D;
	}

	public static boolean isLoggableI() {
		return isLoggable(INFO);
	}

	public static boolean isLoggableW() {
		return isLoggable(WARN);
	}

	public static boolean isLoggableE() {
		return isLoggable(ERROR);
	}

	public static boolean isLoggable(Level level) {
		return impl.isLoggable(level);
	}

	public enum Level {
		DEBUG, INFO, WARN, ERROR
	}
}
