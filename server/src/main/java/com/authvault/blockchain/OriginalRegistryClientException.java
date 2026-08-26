package com.authvault.blockchain;

public class OriginalRegistryClientException extends RuntimeException {

    private final Reason reason;

    public OriginalRegistryClientException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OriginalRegistryClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        DISABLED,
        UNAVAILABLE,
        REGISTRATION_FAILED,
        ASSET_NOT_FOUND,
        DUPLICATE_ASSET,
        DUPLICATE_SHA256,
        INVALID_RESPONSE
    }
}
