package com.vaultchain.dto;

public record ErrorResponse(boolean success, String message) {
    public ErrorResponse(String message) {
        this(false, message == null || message.isEmpty() ? "Internal Server Error" : message);
    }
}
