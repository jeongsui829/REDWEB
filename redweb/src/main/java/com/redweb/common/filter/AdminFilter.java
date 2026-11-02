package com.redweb.common.filter;

import com.redweb.common.constant.SessionKey;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebFilter("/admin/*")
public class AdminFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);
        String servletPath = httpRequest.getServletPath();

        if (session == null || session.getAttribute(SessionKey.ADMIN_USER) == null) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
            return;
        }

        if ("/admin/verify".equals(servletPath)) {
            chain.doFilter(request, response);
            return;
        }

        Object verified = session.getAttribute(SessionKey.ADMIN_ACCESS_VERIFIED);
        if (!(verified instanceof Boolean) || !((Boolean) verified)) {
            String query = httpRequest.getQueryString();
            String target = servletPath + (query == null || query.isBlank() ? "" : "?" + query);
            httpResponse.sendRedirect(httpRequest.getContextPath()
                    + "/admin/verify?redirect="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8));
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
