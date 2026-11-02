package com.scuredweb.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtil {
    private RequestUtil() {
    }

    public static Long getLong(HttpServletRequest request, String name, Long defaultValue) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}


