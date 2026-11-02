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

@WebFilter(urlPatterns = {
        "/mypage/*",
        "/notifications/*",
        "/messages",
        "/comment/*",
        "/messages/*",
        "/profile/guestbook",
        "/report/user",
        "/board/write",
        "/board/edit",
        "/board/delete",
        "/board/recommend",
        "/board/dislike"
})
public class AuthFilter implements Filter {
    private final UserDAO userDAO = new UserDAO();

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        if (session == null || !(session.getAttribute(SessionKey.LOGIN_USER) instanceof UserDTO loginUser)
                || loginUser.getUserId() == null) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
            return;
        }

        UserDTO currentUser;
        try {
            currentUser = userDAO.findById(loginUser.getUserId());
        } catch (Exception exception) {
            httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "계정 상태를 확인할 수 없습니다. 잠시 후 다시 시도하세요.");
            return;
        }

        if (currentUser == null || currentUser.getAuthVersion() != loginUser.getAuthVersion() || !"ACTIVE".equals(currentUser.getStatus())) {
            session.invalidate();
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
            return;
        }

        session.setAttribute(SessionKey.LOGIN_USER, currentUser);
        if (isAdmin(currentUser)) {
            session.setAttribute(SessionKey.ADMIN_USER, currentUser);
        } else {
            session.removeAttribute(SessionKey.ADMIN_USER);
            session.removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED);
            session.removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT);
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private boolean isAdmin(UserDTO user) {
        return RoleCode.ADMIN.equals(user.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(user.getRoleCode());
    }
}

