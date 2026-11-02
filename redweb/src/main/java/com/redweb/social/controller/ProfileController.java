package com.redweb.social.controller;

import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.ViewUtil;
import com.redweb.social.dto.GuestbookEntryDTO;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = {"/profile", "/profile/guestbook"})
public class ProfileController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String nickname = request.getParameter("nickname");
        UserDTO profileUser = SocialService.findUser(nickname);
        if (profileUser == null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        List<GuestbookEntryDTO> guestbookEntries = SocialService.getGuestbookEntries(profileUser.getUserId());

        request.setAttribute("profileUser", profileUser);
        request.setAttribute("guestbookEntries", guestbookEntries);
        request.setAttribute("loginUser", loginUser);
        request.setAttribute("isOwner", loginUser != null && profileUser.getUserId() != null && profileUser.getUserId().equals(loginUser.getUserId()));
        ViewUtil.forward(request, response, "user/profile");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "방명록은 로그인 후 이용할 수 있습니다.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String nickname = request.getParameter("nickname");
        String content = request.getParameter("content");
        UserDTO profileUser = SocialService.findUser(nickname);
        if (profileUser == null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        if (content == null || content.isBlank()) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "방명록 내용을 입력해주세요.");
            response.sendRedirect(request.getContextPath() + "/profile?nickname=" + profileUser.getNickname());
            return;
        }

        SocialService.addGuestbookEntry(loginUser, profileUser, content.trim());
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "방명록이 등록되었습니다.");
        response.sendRedirect(request.getContextPath() + "/profile?nickname=" + profileUser.getNickname());
    }
}
