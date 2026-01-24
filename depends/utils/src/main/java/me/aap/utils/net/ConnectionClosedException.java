package me.aap.utils.net;

import java.io.IOException;

/**
 * @author Andrey Pavlenko
 */
public class ConnectionClosedException extends IOException {

	public ConnectionClosedException() {
	}

	public ConnectionClosedException(String message) {
		super(message);
	}

	public ConnectionClosedException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConnectionClosedException(Throwable cause) {
		super(cause);
	}
}
