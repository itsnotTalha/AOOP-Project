package com.vaultchain.security;

import java.util.Locale;
import java.util.regex.Pattern;

/** Environment variables are strings: jsonwebtoken/ms interprets bare numbers as milliseconds. */
public final class JwtExpiry {
    private JwtExpiry() {}
    private static final Pattern DURATION = Pattern.compile("(-?(?:[0-9]+)?\\.?[0-9]+) *(milliseconds?|msecs?|ms|seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w|years?|yrs?|y)?", Pattern.CASE_INSENSITIVE);

    public static long expiresAt(long issuedAt, String configured) {
        String value = configured == null || configured.isEmpty() ? "7d" : configured;
        var match = DURATION.matcher(value);
        if (value.length() > 100 || !match.matches()) {
            throw new IllegalArgumentException("\"expiresIn\" should be a number of seconds or string representing a timespan eg: \"1d\", \"20h\", 60");
        }
        String unit = match.group(2) == null ? "ms" : match.group(2).toLowerCase(Locale.ROOT);
        double multiplier = switch (unit) {
            case "y", "yr", "yrs", "year", "years" -> 365.25 * 86400000;
            case "w", "week", "weeks" -> 7 * 86400000;
            case "d", "day", "days" -> 86400000;
            case "h", "hr", "hrs", "hour", "hours" -> 3600000;
            case "m", "min", "mins", "minute", "minutes" -> 60000;
            case "s", "sec", "secs", "second", "seconds" -> 1000;
            default -> 1;
        };
        return (long) Math.floor(issuedAt + Double.parseDouble(match.group(1)) * multiplier / 1000);
    }
}
