package com.vaultchain.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** JSON-value coercions used by the Node account validators, not Java bean validation. */
public final class LegacyValues {
    private LegacyValues() {}
    public static final String WHITESPACE = "[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]";
    private static final Pattern TRIM = Pattern.compile("^" + WHITESPACE + "+|" + WHITESPACE + "+$");

    public static boolean truthy(Object value) {
        return value != null && !Boolean.FALSE.equals(value)
                && !(value instanceof Number number && number.doubleValue() == 0)
                && !(value instanceof String string && string.isEmpty());
    }

    public static String text(Object value) {
        return truthy(value) ? string(value) : "";
    }

    private static String string(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?>) return "[object Object]";
        if (value instanceof List<?> list) {
            return list.stream().map(item -> item == null ? "" : string(item)).collect(Collectors.joining(","));
        }
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        return value.toString();
    }

    public static String trim(String value) {
        return TRIM.matcher(value).replaceAll("");
    }
}
