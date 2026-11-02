package com.scuredweb.social.controller;

import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dto.UserDTO;
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
            boolean marked = SocialService.markNotificationsRead(loginUser.getUserId());
            response.setStatus(marked
                    ? HttpServletResponse.SC_NO_CONTENT
                    : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
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
            SocialService.DeleteResult result = SocialService.deleteReadNotifications(loginUser.getUserId());
            request.getSession().setAttribute(result.success() ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                    result.success()
                            ? "읽은 알림 " + result.deletedCount() + "건을 정리했습니다."
                            : "읽은 알림 정리에 실패했습니다. 잠시 후 다시 시도하세요.");
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

