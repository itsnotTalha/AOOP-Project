package com.authvault.exception;

import org.springframework.http.HttpStatus;

public class BlockchainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BlockchainException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public BlockchainException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
