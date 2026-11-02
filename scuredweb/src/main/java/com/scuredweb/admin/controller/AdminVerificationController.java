package com.scuredweb.admin.controller;

import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.RoleCode;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.security.PasswordUtil;
import com.scuredweb.common.security.RateLimitService;
import com.scuredweb.common.util.RedirectUtil;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet("/admin/verify")
public class AdminVerificationController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        UserDTO loginUser = session == null ? null : (UserDTO) session.getAttribute(SessionKey.LOGIN_USER);
        if (!isAdmin(loginUser)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String redirect = RedirectUtil.adminPathOrDefault(request.getParameter("redirect"), "/admin/dashboard");
        request.setAttribute("redirect", redirect);
        request.setAttribute("errorMessage", session.getAttribute(MessageKey.ERROR_MESSAGE));
        session.removeAttribute(MessageKey.ERROR_MESSAGE);
        ViewUtil.forward(request, response, "admin/verify");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        UserDTO adminUser = session == null ? null : (UserDTO) session.getAttribute(SessionKey.LOGIN_USER);
        if (!isAdmin(adminUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String redirect = RedirectUtil.adminPathOrDefault(request.getParameter("redirect"), "/admin/dashboard");
        String clientIp = request.getRemoteAddr();
        String account = adminUser.getLoginId();
        if (!RateLimitService.consumeAuthentication("admin-verify", clientIp, account) || RateLimitService.isBlocked("admin-verify", clientIp, account)) {
            session.setAttribute(MessageKey.ERROR_MESSAGE, "인증 시도가 일시적으로 제한되었습니다. 잠시 후 다시 시도하세요.");
            redirectToVerify(request, response, redirect);
            return;
        }

        if (PasswordUtil.verifyPassword(request.getParameter("password"), adminUser.getPasswordHash())) {
            RateLimitService.recordSuccess("admin-verify", clientIp, account);
            request.changeSessionId();
            session.setAttribute(SessionKey.ADMIN_ACCESS_VERIFIED, Boolean.TRUE);
            session.setAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT, System.currentTimeMillis());
            response.sendRedirect(request.getContextPath() + redirect);
            return;
        }

        RateLimitService.recordFailure("admin-verify", clientIp, account);
        session.setAttribute(MessageKey.ERROR_MESSAGE, "관리자 비밀번호가 일치하지 않습니다.");
        redirectToVerify(request, response, redirect);
    }

    private void redirectToVerify(HttpServletRequest request, HttpServletResponse response, String redirect) throws IOException {
        response.sendRedirect(request.getContextPath()
                + "/admin/verify?redirect="
                + URLEncoder.encode(redirect, StandardCharsets.UTF_8));
    }

    private boolean isAdmin(UserDTO user) {
        return user != null
                && (RoleCode.ADMIN.equals(user.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(user.getRoleCode()));
    }
}

