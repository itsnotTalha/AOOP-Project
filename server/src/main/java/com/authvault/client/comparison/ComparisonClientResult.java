package com.authvault.client.comparison;

public record ComparisonClientResult<T>(Status status, T body) {

    public enum Status {
        SUCCESS,
        DISABLED,
        UNAVAILABLE
    }

    public static <T> ComparisonClientResult<T> success(T body) {
        return new ComparisonClientResult<>(Status.SUCCESS, body);
    }

    public static <T> ComparisonClientResult<T> disabled() {
        return new ComparisonClientResult<>(Status.DISABLED, null);
    }

    public static <T> ComparisonClientResult<T> unavailable() {
        return new ComparisonClientResult<>(Status.UNAVAILABLE, null);
    }
}
