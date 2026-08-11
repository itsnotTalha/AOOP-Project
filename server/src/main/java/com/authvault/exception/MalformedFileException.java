package com.authvault.exception;

public class MalformedFileException extends RuntimeException {

    public MalformedFileException(String message) {
        super(message);
    }

    public MalformedFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
