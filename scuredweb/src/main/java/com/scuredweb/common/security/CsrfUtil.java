package com.scuredweb.common.security;

import com.scuredweb.common.util.HtmlUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.security.SecureRandom;
import java.util.Base64;

public final class CsrfUtil {
    public static final String PARAMETER_NAME = "_csrf";
    public static final String SESSION_ATTRIBUTE = "csrfToken";
    public static final String REQUEST_ATTRIBUTE = "csrfToken";
    public static final String REQUEST_PARAMETER_ATTRIBUTE = "csrfParameterName";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfUtil() {
    }

    public static String ensureToken(HttpServletRequest request) {
        HttpSession session = request.getSession();
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof String token && !token.isBlank()) {
            expose(request, token);
            return token;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, token);
        expose(request, token);
        return token;
    }

    public static boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_ATTRIBUTE);
        String actual = request.getParameter(PARAMETER_NAME);
        return expected instanceof String token && actual != null && constantTimeEquals(token, actual);
    }

    public static String hiddenInput(HttpServletRequest request) {
        return "<input type=\"hidden\" name=\"" + PARAMETER_NAME + "\" value=\""
                + HtmlUtil.escapeAttribute(ensureToken(request)) + "\">";
    }

    public static void exposeExistingTokenIfPresent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof String token && !token.isBlank()) {
            expose(request, token);
        }
    }

    private static void expose(HttpServletRequest request, String token) {
        request.setAttribute(REQUEST_ATTRIBUTE, token);
        request.setAttribute(REQUEST_PARAMETER_ATTRIBUTE, PARAMETER_NAME);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ actual.charAt(i);
        }
        return result == 0;
    }
}

