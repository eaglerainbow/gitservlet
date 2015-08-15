package com.github.eaglerainbow.gitservlet;

public class LocalInternalServerException extends Exception {

	public LocalInternalServerException(String message, Throwable cause) {
		super(message, cause);
	}

	public LocalInternalServerException(String message) {
		super(message);
	}

	public LocalInternalServerException(Throwable cause) {
		super(cause);
	}
	
}
