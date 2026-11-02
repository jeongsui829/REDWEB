package com.scuredweb.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("TRACE".equalsIgnoreCase(httpRequest.getMethod()) || "CONNECT".equalsIgnoreCase(httpRequest.getMethod())) { httpResponse.sendError(405); return; }
        httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'");
        if (isSensitive(httpRequest)) {
            httpResponse.setHeader("Cache-Control", "no-store");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Referrer-Policy", "no-referrer");
        } else {
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        chain.doFilter(request, response);
    }

    private boolean isSensitive(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/mypage")
                || path.startsWith("/admin")
                || path.startsWith("/messages")
                || path.equals("/login")
                || path.equals("/register")
                || path.equals("/find-id")
                || path.equals("/find-password")
                || path.equals("/reset-password")
                || path.equals("/verify-email");
    }
}

