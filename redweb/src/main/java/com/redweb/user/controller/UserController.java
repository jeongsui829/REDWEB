package com.redweb.user.controller;

import com.redweb.board.dto.PostDTO;
import com.redweb.comment.dto.CommentDTO;
import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.UploadPathUtil;
import com.redweb.common.util.ViewUtil;
import com.redweb.notification.dto.NotificationDTO;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.LoginHistoryDTO;
import com.redweb.user.dto.UserDTO;
import com.redweb.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@MultipartConfig
@WebServlet(urlPatterns = {
        "/mypage",
        "/mypage/profile",
        "/mypage/password",
        "/mypage/login-history",
        "/mypage/notifications",
        "/mypage/posts",
        "/mypage/comments",
        "/mypage/withdraw"
})
public class UserController extends HttpServlet {
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        UserDTO user = userService.getUser(loginUser.getUserId(), loginUser);
        String currentSection = resolveSection(request.getServletPath());
        List<NotificationDTO> notifications = new ArrayList<>(userService.getNotifications(loginUser.getUserId()));
        if ("notifications".equals(currentSection)) {
            SocialService.markNotificationsRead(loginUser.getUserId());
        }
        List<LoginHistoryDTO> loginHistory = userService.getLoginHistory(loginUser.getUserId());
        List<PostDTO> myPosts = mergePosts(request, loginUser);
        List<CommentDTO> myComments = mergeComments(request, loginUser);

        request.setAttribute("user", user);
        request.setAttribute("notifications", notifications);
        request.setAttribute("loginHistory", loginHistory);
        request.setAttribute("myPosts", myPosts);
        request.setAttribute("myComments", myComments);
        request.setAttribute("postCount", myPosts.size());
        request.setAttribute("commentCount", myComments.size());
        request.setAttribute("unreadNotificationCount", notifications.stream().filter(notification -> !notification.isRead()).count());
        request.setAttribute("currentSection", currentSection);
        ViewUtil.forward(request, response, "user/mypage");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String servletPath = request.getServletPath();

        if ("/mypage/profile".equals(servletPath)) {
            handleProfileUpload(request, response);
            return;
        }

        if ("/mypage/password".equals(servletPath)) {
            handlePasswordChange(request, response);
            return;
        }

        if (!"/mypage/withdraw".equals(servletPath)) {
            response.sendRedirect(request.getContextPath() + "/mypage");
            return;
        }

        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        boolean withdrawn = userService.withdraw(loginUser);
        if (!withdrawn) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "회원 탈퇴 처리 중 문제가 발생했습니다.");
            response.sendRedirect(request.getContextPath() + "/mypage/withdraw");
            return;
        }

        request.getSession().invalidate();
        request.getSession(true).setAttribute(MessageKey.FLASH_MESSAGE, "회원 탈퇴가 완료되었습니다.");
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private void handleProfileUpload(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Part imagePart = request.getPart("profileImage");
        String nickname = request.getParameter("nickname");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String bio = request.getParameter("bio");

        boolean profileUpdated = userService.updateProfile(loginUser, nickname, email, phone);
        if (!profileUpdated) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "프로필 정보를 DB에 저장하지 못했습니다. 닉네임/이메일 중복 여부와 DB 연결을 확인해 주세요.");
            response.sendRedirect(request.getContextPath() + "/mypage/profile");
            return;
        }

        SocialService.updateBio(loginUser, bio != null ? bio.trim() : "");

        if (imagePart == null || imagePart.getSize() <= 0) {
            request.getSession().setAttribute(SessionKey.LOGIN_USER, loginUser);
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "프로필 정보가 저장되었습니다.");
            response.sendRedirect(request.getContextPath() + "/mypage/profile");
            return;
        }

        String submittedFileName = imagePart.getSubmittedFileName();
        String extension = getExtension(submittedFileName);
        String savedName = UUID.randomUUID() + extension;
        Path uploadDirectory = UploadPathUtil.getProfilesDirectory();

        Path target = uploadDirectory.resolve(savedName);
        try (var inputStream = imagePart.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String publicPath = request.getContextPath() + "/uploads/profiles/" + savedName;
        userService.updateProfileImage(loginUser, publicPath);
        request.getSession().setAttribute(SessionKey.LOGIN_USER, loginUser);
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "프로필 이미지와 정보가 적용되었습니다.");
        response.sendRedirect(request.getContextPath() + "/mypage/profile");
    }

    private void handlePasswordChange(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        String errorMessage = userService.changePassword(loginUser, currentPassword, newPassword, confirmPassword);

        if (errorMessage != null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, errorMessage);
            response.sendRedirect(request.getContextPath() + "/mypage/password");
            return;
        }

        request.getSession().setAttribute(SessionKey.LOGIN_USER, loginUser);
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "비밀번호가 변경되었습니다.");
        response.sendRedirect(request.getContextPath() + "/mypage/password");
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".png";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }

    private List<PostDTO> mergePosts(HttpServletRequest request, UserDTO loginUser) {
        List<PostDTO> posts = new ArrayList<>(userService.getMyPosts(loginUser.getUserId()));
        for (List<PostDTO> items : getLocalPosts(request).values()) {
            for (PostDTO post : items) {
                boolean sameUserId = loginUser.getUserId() != null && loginUser.getUserId().equals(post.getUserId());
                boolean sameSessionAuthor = request.getSession().getId().equals(post.getSessionOwnerId())
                        && loginUser.getNickname() != null
                        && loginUser.getNickname().equals(post.getAuthorNickname());
                if (sameUserId || sameSessionAuthor) {
                    posts.add(post);
                }
            }
        }
        return posts;
    }

    private List<CommentDTO> mergeComments(HttpServletRequest request, UserDTO loginUser) {
        List<CommentDTO> comments = new ArrayList<>(userService.getMyComments(loginUser.getUserId()));
        for (List<CommentDTO> items : getLocalComments(request).values()) {
            for (CommentDTO comment : items) {
                boolean sameUserId = loginUser.getUserId() != null && loginUser.getUserId().equals(comment.getUserId());
                boolean sameNickname = loginUser.getNickname() != null && loginUser.getNickname().equals(comment.getAuthorNickname());
                if (sameUserId || sameNickname) {
                    comments.add(comment);
                }
            }
        }
        return comments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<PostDTO>> getLocalPosts(HttpServletRequest request) {
        Object stored = request.getServletContext().getAttribute("localPosts");
        if (stored instanceof Map<?, ?>) {
            return (Map<String, List<PostDTO>>) stored;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<CommentDTO>> getLocalComments(HttpServletRequest request) {
        Object stored = request.getServletContext().getAttribute("localComments");
        if (stored instanceof Map<?, ?>) {
            return (Map<Long, List<CommentDTO>>) stored;
        }
        return Map.of();
    }

    private String resolveSection(String servletPath) {
        return switch (servletPath) {
            case "/mypage/profile" -> "profile";
            case "/mypage/password" -> "password";
            case "/mypage/login-history" -> "login-history";
            case "/mypage/notifications" -> "notifications";
            case "/mypage/posts" -> "posts";
            case "/mypage/comments" -> "comments";
            case "/mypage/withdraw" -> "withdraw";
            default -> "overview";
        };
    }
}
