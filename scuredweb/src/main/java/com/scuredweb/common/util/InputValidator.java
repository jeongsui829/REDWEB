package com.scuredweb.common.util;

import java.util.Set;
import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern BOARD_CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,49}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,180}\\.[^@\\s]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-()\\s]{0,30}$");
    private static final Set<String> SORTS = Set.of("latest", "views", "likes");

    private InputValidator() {
    }

    public static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean hasLength(String value, int minimumLength, int maximumLength) {
        if (value == null) {
            return minimumLength == 0;
        }
        int length = value.trim().length();
        return length >= minimumLength && length <= maximumLength;
    }

    public static boolean isBoardCode(String value) {
        return value != null && BOARD_CODE.matcher(value.trim()).matches();
    }

    public static String boardCodeOrDefault(String value, String defaultValue) {
        String trimmed = trimmed(value);
        return BOARD_CODE.matcher(trimmed).matches() ? trimmed : defaultValue;
    }

    public static String sortOrDefault(String value, String defaultValue) {
        String limited = limit(value, 20);
        return SORTS.contains(limited) ? limited : defaultValue;
    }

    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value.trim()).matches() && value.trim().length() <= 150;
    }

    public static boolean isPhone(String value) {
        return value == null || PHONE.matcher(value.trim()).matches();
    }
}

