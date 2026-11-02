package com.scuredweb.social.controller;

import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.social.dto.GuestbookEntryDTO;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.social.dto.PublicProfileDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@WebServlet(urlPatterns = {"/profile", "/profile/guestbook"})
public class ProfileController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String nickname = request.getParameter("nickname");
        PublicProfileDTO profileUser = SocialService.findPublicProfileByNickname(nickname);
        if (profileUser == null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        HttpSession session = request.getSession(false);
        UserDTO loginUser = session == null ? null : (UserDTO) session.getAttribute(SessionKey.LOGIN_USER);
        List<GuestbookEntryDTO> guestbookEntries;
        try {
            guestbookEntries = SocialService.getGuestbookEntries(profileUser.getUserId());
        } catch (DataAccessException exception) {
            guestbookEntries = List.of();
            request.setAttribute("guestbookLoadError", true);
        }

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
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "방명록은 로그인 후 작성할 수 있습니다.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String nickname = request.getParameter("nickname");
        String content = request.getParameter("content");
        PublicProfileDTO profileUser = SocialService.findPublicProfileByNickname(nickname);
        if (profileUser == null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        if (!InputValidator.hasLength(content, 1, 2000)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "방명록은 1자 이상 2000자 이하로 입력하세요.");
            response.sendRedirect(profileRedirect(request, profileUser));
            return;
        }

        boolean added = SocialService.addGuestbookEntry(loginUser, profileUser, content.trim());
        request.getSession().setAttribute(added ? MessageKey.FLASH_MESSAGE : MessageKey.ERROR_MESSAGE,
                added ? "방명록이 등록되었습니다." : "방명록 등록에 실패했습니다. 잠시 후 다시 시도하세요.");
        response.sendRedirect(profileRedirect(request, profileUser));
    }

    private String profileRedirect(HttpServletRequest request, PublicProfileDTO profileUser) {
        return request.getContextPath() + "/profile?nickname="
                + URLEncoder.encode(profileUser.getNickname(), StandardCharsets.UTF_8);
    }
}

