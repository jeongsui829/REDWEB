package com.scuredweb.common.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlUtil {
    private static final Pattern SAFE_IMAGE = Pattern.compile("<img\\s+[^>]*src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private HtmlUtil() {
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    public static String escapeAttribute(String value) {
        return escapeHtml(value);
    }

    public static String escapeUrlParam(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String textWithLineBreaks(String value) {
        return escapeHtml(value).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    public static String safePostContent(String value, String contextPath) {
        if (value == null) {
            return "";
        }
        Matcher matcher = SAFE_IMAGE.matcher(value);
        StringBuilder builder = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            builder.append(escapeTextSegment(value.substring(last, matcher.start())));
            String src = matcher.group(1);
            if (isSafeUploadImage(src, contextPath)) {
                String renderedSrc = renderUploadImageSrc(src, contextPath);
                builder.append("<img src=\"")
                        .append(escapeAttribute(renderedSrc))
                        .append("\" alt=\"")
                        .append(escapeAttribute(matcher.group(2)))
                        .append("\">");
            }
            last = matcher.end();
        }
        builder.append(escapeTextSegment(value.substring(last)));
        return builder.toString();
    }

    private static String escapeTextSegment(String value) {
        return escapeHtml(value).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    private static boolean isSafeUploadImage(String src, String contextPath) {
        if (src == null || src.contains("\r") || src.contains("\n") || src.contains("\\") || src.startsWith("//")) {
            return false;
        }
        String contextPrefix = (contextPath == null ? "" : contextPath) + "/files/image?savedName=";
        String lower = src.toLowerCase();
        return (src.startsWith("/files/image?savedName=") || src.startsWith(contextPrefix))
                && !src.contains("&")
                && (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif"));
    }

    private static String renderUploadImageSrc(String src, String contextPath) {
        if (src.startsWith("/files/image?savedName=")) {
            return (contextPath == null ? "" : contextPath) + src;
        }
        return src;
    }
}

