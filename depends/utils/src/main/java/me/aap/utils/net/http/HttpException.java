package me.aap.utils.net.http;

/**
 * @author Andrey Pavlenko
 */
public class HttpException extends Exception {

	public HttpException() {
	}

	public HttpException(String message) {
		super(message);
	}

	public HttpException(String message, Throwable cause) {
		super(message, cause);
	}

	public HttpException(Throwable cause) {
		super(cause);
	}
}
