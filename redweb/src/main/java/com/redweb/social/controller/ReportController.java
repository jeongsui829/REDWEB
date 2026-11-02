package com.redweb.social.controller;

import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.RoleCode;
import com.redweb.common.constant.SessionKey;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/report/user")
public class ReportController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "신고는 로그인 후 사용할 수 있습니다.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        if (RoleCode.ADMIN.equals(loginUser.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(loginUser.getRoleCode())) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "관리자 계정은 신고 기능을 사용할 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String targetNickname = request.getParameter("nickname");
        if (targetNickname == null || targetNickname.isBlank()) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "신고 대상을 찾을 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        UserDTO targetUser = SocialService.findUser(targetNickname.trim());
        if (targetUser != null && loginUser.getUserId() != null && loginUser.getUserId().equals(targetUser.getUserId())) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "자기 자신은 신고할 수 없습니다.");
            redirectBack(request, response);
            return;
        }

        SocialService.submitUserReport(loginUser, targetNickname.trim(), "사용자 신고 접수");
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "신고가 접수되었습니다. 운영진이 확인할 예정입니다.");
        redirectBack(request, response);
    }

    private void redirectBack(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            response.sendRedirect(referer);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/home");
    }
}
