package me.aap.utils.net.http;

/**
 * @author Andrey Pavlenko
 */
public interface HttpStatusCode {
	int OK = 200;
	int PARTIAL = 206;
	int MOVED_PERMANENTLY = 301;
	int FOUND = 302;
	int NOT_MODIFIED = 304;
	int TEMPORARY_REDIRECT = 307;
	int PERMANENT_REDIRECT = 308;
	int BAD_REQUEST = 400;
	int UNAUTHORIZED = 401;
	int FORBIDDEN = 403;
	int NOT_FOUND = 404;
	int METHOD_NOT_ALLOWED = 405;
	int SERVER_ERROR = 500;
	int SERVICE_UNAVAILABLE = 503;
}
