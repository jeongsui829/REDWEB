package com.redweb.admin.controller;

import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.ViewUtil;
import com.redweb.user.dto.UserDTO;
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
        if (session == null || session.getAttribute(SessionKey.ADMIN_USER) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String redirect = request.getParameter("redirect");
        request.setAttribute("redirect", (redirect == null || redirect.isBlank()) ? "/admin/dashboard" : redirect);
        request.setAttribute("errorMessage", session.getAttribute(MessageKey.ERROR_MESSAGE));
        session.removeAttribute(MessageKey.ERROR_MESSAGE);
        ViewUtil.forward(request, response, "admin/verify");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        UserDTO adminUser = (UserDTO) session.getAttribute(SessionKey.ADMIN_USER);
        if (adminUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String password = request.getParameter("password");
        String redirect = request.getParameter("redirect");
        if (password != null && password.equals(adminUser.getPasswordHash())) {
            session.setAttribute(SessionKey.ADMIN_ACCESS_VERIFIED, Boolean.TRUE);
            response.sendRedirect(request.getContextPath()
                    + ((redirect == null || redirect.isBlank()) ? "/admin/dashboard" : redirect));
            return;
        }

        session.setAttribute(MessageKey.ERROR_MESSAGE, "관리자 비밀번호가 일치하지 않습니다.");
        String safeRedirect = (redirect == null || redirect.isBlank()) ? "/admin/dashboard" : redirect;
        response.sendRedirect(request.getContextPath()
                + "/admin/verify?redirect="
                + URLEncoder.encode(safeRedirect, StandardCharsets.UTF_8));
    }
}
