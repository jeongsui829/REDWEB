package com.scuredweb.common.filter;

import com.scuredweb.common.constant.RoleCode;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;
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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebFilter("/admin/*")
public class AdminFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(AdminFilter.class.getName());
    private final UserDAO userDAO;

    public AdminFilter() {
        this(new UserDAO());
    }

    AdminFilter(UserDAO userDAO) {
        this.userDAO = userDAO;
    }
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

        if (session == null) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
            return;
        }

        Object loginUserAttribute = session.getAttribute(SessionKey.LOGIN_USER);
        if (!(loginUserAttribute instanceof UserDTO loginUser) || loginUser.getUserId() == null) {
            clearAdminSession(session);
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        UserDTO currentUser;
        try {
            currentUser = loadCurrentAdmin(loginUser);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Failed to load current administrator", exception);
            httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "관리자 계정 상태를 확인할 수 없습니다. 잠시 후 다시 시도하세요.");
            return;
        }
        if (currentUser == null) {
            session.invalidate();
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (currentUser.getAuthVersion() != loginUser.getAuthVersion() || !"ACTIVE".equals(currentUser.getStatus())) {
            session.invalidate();
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        session.setAttribute(SessionKey.LOGIN_USER, currentUser);
        if (!(RoleCode.ADMIN.equals(currentUser.getRoleCode())
                || RoleCode.SUPER_ADMIN.equals(currentUser.getRoleCode()))) {
            clearAdminSession(session);
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        session.setAttribute(SessionKey.ADMIN_USER, currentUser);
        if ("/admin/verify".equals(servletPath)) {
            chain.doFilter(request, response);
            return;
        }

        if (!com.scuredweb.common.security.AuthorizationUtil.hasFreshVerification(session)) {
            clearAdminVerification(session);
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

    private UserDTO loadCurrentAdmin(UserDTO loginUser) throws Exception {
        if (loginUser.getUserId() == null) {
            return null;
        }
        return userDAO.findById(loginUser.getUserId());
    }

    private void clearAdminSession(HttpSession session) {
        clearAdminVerification(session);
        session.removeAttribute(SessionKey.ADMIN_USER);
    }

    private void clearAdminVerification(HttpSession session) {
        session.removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED);
        session.removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT);
    }
}

