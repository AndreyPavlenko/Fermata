package me.aap.utils.vfs;

import java.io.IOException;

/**
 * @author Andrey Pavlenko
 */
public class VfsException extends IOException {

	public VfsException() {
	}

	public VfsException(String message) {
		super(message);
	}

	public VfsException(String message, Throwable cause) {
		super(message, cause);
	}

	public VfsException(Throwable cause) {
		super(cause);
	}
}
