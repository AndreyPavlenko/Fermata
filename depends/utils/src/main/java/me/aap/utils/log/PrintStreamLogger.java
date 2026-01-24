package me.aap.utils.log;

import java.io.PrintStream;

/**
 * @author Andrey Pavlenko
 */
class PrintStreamLogger extends Logger {
	private final PrintStream ps;

	public PrintStreamLogger(PrintStream ps) {
		this.ps = ps;
	}

	@Override
	protected boolean addLevelName() {
		return true;
	}

	@Override
	public void logDebug(StringBuilder msg) {
		log(msg);
	}

	@Override
	public void logDebug(StringBuilder msg, Throwable ex) {
		log(msg, ex);
	}

	@Override
	public void logInfo(StringBuilder msg) {
		log(msg);
	}

	@Override
	public void logInfo(StringBuilder msg, Throwable ex) {
		log(msg, ex);
	}

	@Override
	public void logWarn(StringBuilder msg) {
		log(msg);
	}

	@Override
	public void logWarn(StringBuilder msg, Throwable ex) {
		log(msg, ex);
	}

	@Override
	public void logError(StringBuilder msg) {
		log(msg);
	}

	@Override
	public void logError(StringBuilder msg, Throwable ex) {
		log(msg, ex);
	}

	@Override
	public boolean isLoggable(Log.Level level) {
		return true;
	}

	private synchronized void log(StringBuilder msg) {
		ps.println(msg);
	}

	private synchronized void log(StringBuilder msg, Throwable ex) {
		ps.println(msg);
		ex.printStackTrace(ps);
	}
}
