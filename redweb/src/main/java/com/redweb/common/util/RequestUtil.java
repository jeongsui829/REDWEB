package com.redweb.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtil {
    private RequestUtil() {
    }

    public static Long getLong(HttpServletRequest request, String name, Long defaultValue) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }
}

