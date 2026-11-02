package com.scuredweb.social.controller;

import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.social.dto.MessageDTO;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.social.dto.PublicProfileDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@WebServlet(urlPatterns = {"/messages", "/messages/compose", "/messages/send", "/messages/delete", "/messages/clear-read", "/messages/read"})
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
        List<MessageDTO> messages;
        try {
            messages = SocialService.getMessages(loginUser.getUserId());
        } catch (DataAccessException exception) {
            messages = List.of();
            request.setAttribute("messagesLoadError", true);
        }
        PublicProfileDTO targetUser = SocialService.findUser(request.getParameter("to"));
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
        if ("/messages/read".equals(servletPath)) {
            response.setStatus(SocialService.markMessagesRead(loginUser.getUserId()) ? 204 : 503);
            return;
        }
        if ("/messages/delete".equals(servletPath)) {
            Long messageId = parseLong(request.getParameter("messageId"));
            boolean deleted = SocialService.deleteMessage(loginUser.getUserId(), messageId);
            request.getSession().setAttribute(deleted ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                    deleted ? "쪽지를 삭제했습니다." : "쪽지 삭제에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }

        if ("/messages/clear-read".equals(servletPath)) {
            SocialService.DeleteResult result = SocialService.deleteReadMessages(loginUser.getUserId());
            request.getSession().setAttribute(result.success() ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                    result.success()
                            ? "읽은 쪽지 " + result.deletedCount() + "건을 정리했습니다."
                            : "읽은 쪽지 정리에 실패했습니다. 잠시 후 다시 시도하세요.");
            response.sendRedirect(request.getContextPath() + "/messages");
            return;
        }

        PublicProfileDTO targetUser = SocialService.findUser(request.getParameter("receiver"));
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
        String resolvedTitle = (title == null || title.isBlank()) ? "새 쪽지" : title.trim();
        if (!InputValidator.hasLength(resolvedTitle, 1, 200)
                || !InputValidator.hasLength(content, 1, 2000)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE,
                    "쪽지 제목은 200자 이하, 내용은 1자 이상 2000자 이하로 입력하세요.");
            response.sendRedirect(request.getContextPath() + "/messages/compose?to=" + encode(targetUser.getNickname()));
            return;
        }

        boolean sent = SocialService.sendMessage(loginUser, targetUser, resolvedTitle, content.trim());
        request.getSession().setAttribute(sent ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                sent ? "쪽지를 보냈습니다." : "쪽지 전송에 실패했습니다. 잠시 후 다시 시도하세요.");
        response.sendRedirect(request.getContextPath() + "/messages");
    }

    private boolean isSameUser(UserDTO loginUser, PublicProfileDTO targetUser) {
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

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

