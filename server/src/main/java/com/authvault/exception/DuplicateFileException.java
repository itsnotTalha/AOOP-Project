package com.authvault.exception;

public class DuplicateFileException extends DuplicateResourceException {

    public static final String CODE = "DUPLICATE_FILE";

    public DuplicateFileException() {
        super("A file with the same SHA-256 hash already exists");
    }

    public DuplicateFileException(Throwable cause) {
        super("A file with the same SHA-256 hash already exists", cause);
    }
}
