package com.authvault.client.ai;

public record AiClientResult<T>(Status status, T body) {

    public enum Status {
        SUCCESS,
        DISABLED,
        UNAVAILABLE
    }

    public static <T> AiClientResult<T> success(T body) {
        return new AiClientResult<>(Status.SUCCESS, body);
    }

    public static <T> AiClientResult<T> disabled() {
        return new AiClientResult<>(Status.DISABLED, null);
    }

    public static <T> AiClientResult<T> unavailable() {
        return new AiClientResult<>(Status.UNAVAILABLE, null);
    }
}
