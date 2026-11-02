package com.redweb.social.controller;

import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.SessionKey;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/notifications/read", "/notifications/delete", "/notifications/clear-read"})
public class NotificationController extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String servletPath = request.getServletPath();
        if ("/notifications/read".equals(servletPath)) {
            SocialService.markNotificationsRead(loginUser.getUserId());
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        if ("/notifications/delete".equals(servletPath)) {
            Long notificationId = parseLong(request.getParameter("notificationId"));
            boolean deleted = SocialService.deleteNotification(loginUser.getUserId(), notificationId);
            request.getSession().setAttribute(deleted ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                    deleted ? "알림을 삭제했습니다." : "알림 삭제에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/mypage/notifications");
            return;
        }

        if ("/notifications/clear-read".equals(servletPath)) {
            int deletedCount = SocialService.deleteReadNotifications(loginUser.getUserId());
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "읽은 알림 " + deletedCount + "건을 정리했습니다.");
            response.sendRedirect(request.getContextPath() + "/mypage/notifications");
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
