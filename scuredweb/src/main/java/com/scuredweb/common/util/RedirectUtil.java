package com.scuredweb.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RedirectUtil {
    private RedirectUtil() {
    }

    public static String adminPathOrDefault(String value, String defaultPath) {
        if (isUnsafePath(value) || !value.startsWith("/admin/")) {
            return defaultPath;
        }
        return value;
    }

    public static String internalPathOrDefault(String value, String defaultPath) {
        if (isUnsafePath(value)) {
            return defaultPath;
        }
        return value;
    }

    public static String localRefererPath(HttpServletRequest request, String defaultPath) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return defaultPath;
        }
        String contextPath = request.getContextPath();
        String origin = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (!(("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443))) {
            origin += ":" + port;
        }
        String prefix = origin + contextPath;
        if (!referer.startsWith(prefix)) {
            return defaultPath;
        }
        return internalPathOrDefault(referer.substring(prefix.length()), defaultPath);
    }

    private static boolean isUnsafePath(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        return !trimmed.startsWith("/")
                || trimmed.startsWith("//")
                || trimmed.contains("\\")
                || trimmed.contains("\r")
                || trimmed.contains("\n")
                || trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8);
    }
}

