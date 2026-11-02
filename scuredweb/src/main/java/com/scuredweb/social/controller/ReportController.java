package com.scuredweb.social.controller;

import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.security.AuthorizationUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.common.util.RedirectUtil;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.social.dto.PublicProfileDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/report/user")
public class ReportController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "신고하려면 로그인이 필요합니다.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        if (AuthorizationUtil.isCurrentAdmin(loginUser)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "관리자 계정은 사용자 신고를 등록할 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String targetNickname = InputValidator.trimmed(request.getParameter("nickname"));
        if (!InputValidator.hasLength(targetNickname, 1, 100)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "신고 대상을 찾을 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        PublicProfileDTO targetUser = SocialService.findUser(targetNickname);
        if (targetUser != null && loginUser.getUserId() != null && loginUser.getUserId().equals(targetUser.getUserId())) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "자기 자신은 신고할 수 없습니다.");
            redirectBack(request, response);
            return;
        }

        boolean submitted = SocialService.submitUserReport(loginUser, targetNickname, "사용자 신고 접수");
        request.getSession().setAttribute(submitted ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                submitted ? "신고가 접수되었습니다." : "신고 접수에 실패했습니다. 잠시 후 다시 시도하세요.");
        redirectBack(request, response);
    }

    private void redirectBack(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + RedirectUtil.localRefererPath(request, "/home"));
    }
}

