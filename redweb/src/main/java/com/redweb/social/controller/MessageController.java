package com.redweb.social.controller;

import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.ViewUtil;
import com.redweb.social.dto.MessageDTO;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = {"/messages", "/messages/compose", "/messages/send", "/messages/delete", "/messages/clear-read"})
public class MessageController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "쪽지는 로그인 후 사용할 수 있습니다.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String servletPath = request.getServletPath();
        List<MessageDTO> messages = SocialService.getMessages(loginUser.getUserId());
        SocialService.markMessagesRead(loginUser.getUserId());
        UserDTO targetUser = SocialService.findUser(request.getParameter("to"));
        String replyTitle = request.getParameter("replyTitle");

        if (targetUser != null && isSameUser(loginUser, targetUser)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "자기 자신에게는 쪽지를 보낼 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }

        request.setAttribute("messages", messages);
        request.setAttribute("loginUser", loginUser);
        request.setAttribute("targetUser", targetUser);
        request.setAttribute("replyTitle", replyTitle);
        request.setAttribute("composeMode", "/messages/compose".equals(servletPath));
        ViewUtil.forward(request, response, "user/messages");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "쪽지는 로그인 후 사용할 수 있습니다.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String servletPath = request.getServletPath();
        if ("/messages/delete".equals(servletPath)) {
            Long messageId = parseLong(request.getParameter("messageId"));
            boolean deleted = SocialService.deleteMessage(loginUser.getUserId(), messageId);
            request.getSession().setAttribute(deleted ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                    deleted ? "쪽지를 삭제했습니다." : "쪽지 삭제에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }

        if ("/messages/clear-read".equals(servletPath)) {
            int deletedCount = SocialService.deleteReadMessages(loginUser.getUserId());
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "읽은 쪽지 " + deletedCount + "건을 정리했습니다.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }

        UserDTO targetUser = SocialService.findUser(request.getParameter("receiver"));
        String title = request.getParameter("title");
        String content = request.getParameter("content");

        if (targetUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "대상 사용자를 찾을 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }
        if (isSameUser(loginUser, targetUser)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "자기 자신에게는 쪽지를 보낼 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }
        if (content == null || content.isBlank()) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "쪽지 내용을 입력해 주세요.");
            response.sendRedirect(request.getContextPath() + "/messages/compose?to=" + targetUser.getNickname());
            return;
        }

        String resolvedTitle = (title == null || title.isBlank()) ? "새 쪽지" : title.trim();
        SocialService.sendMessage(loginUser, targetUser, resolvedTitle, content.trim());
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "쪽지를 보냈습니다.");
        response.sendRedirect(request.getContextPath() + "/messages");
    }

    private boolean isSameUser(UserDTO loginUser, UserDTO targetUser) {
        return loginUser != null
                && targetUser != null
                && loginUser.getUserId() != null
                && loginUser.getUserId().equals(targetUser.getUserId());
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
