package com.scuredweb.common.filter;

import com.scuredweb.common.security.CsrfUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

@WebFilter("/*")
public class CsrfFilter implements Filter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("TRACE".equalsIgnoreCase(httpRequest.getMethod()) || "CONNECT".equalsIgnoreCase(httpRequest.getMethod())) { httpResponse.sendError(405); return; }
        if (shouldSkip(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (!SAFE_METHODS.contains(httpRequest.getMethod()) && !CsrfUtil.isValid(httpRequest)) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        CsrfUtil.exposeExistingTokenIfPresent(httpRequest);
        chain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/assets/")
                || path.equals("/favicon.ico")
                || path.equals("/files/download")
                || path.equals("/files/image")
                || path.equals("/profile/image")
                || path.startsWith("/error");
    }
}

