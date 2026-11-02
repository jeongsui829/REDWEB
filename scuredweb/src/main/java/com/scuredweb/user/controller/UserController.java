package com.scuredweb.user.controller;

import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.comment.dto.CommentDTO;
import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.util.FileValidationUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.common.util.UploadPathUtil;
import com.scuredweb.common.util.StoredFileCleanup;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.notification.dto.NotificationDTO;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dto.LoginHistoryDTO;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.user.service.UserService;
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
import java.util.UUID;

@MultipartConfig(
        fileSizeThreshold = 512 * 1024,
        maxFileSize = FileValidationUtil.MAX_PROFILE_IMAGE_SIZE,
        maxRequestSize = FileValidationUtil.MAX_PROFILE_IMAGE_SIZE + 512 * 1024
)
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
    private static final String DEFAULT_PROFILE_IMAGE = "/assets/images/profile-default.png";
    private static final String PROFILE_IMAGE_PREFIX = "/profile/image?savedName=";
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        UserDTO user = userService.getUser(loginUser.getUserId());
        String currentSection = resolveSection(request.getServletPath());
        List<NotificationDTO> notifications = new ArrayList<>(userService.getNotifications(loginUser.getUserId()));
        List<LoginHistoryDTO> loginHistory = userService.getLoginHistory(loginUser.getUserId());
        List<PostDTO> myPosts = userService.getMyPosts(loginUser.getUserId());
        List<CommentDTO> myComments = userService.getMyComments(loginUser.getUserId());

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

        if (!"MEMBER".equals(loginUser.getRoleCode())) { response.sendError(403, "관리자 계정은 관리자 전용 경로에서 관리하세요."); return; }
        boolean withdrawn = userService.withdraw(loginUser, request.getParameter("currentPassword"));
        if (!withdrawn) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "회원 탈퇴 처리 중 오류가 발생했습니다.");
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
        boolean hasImage = imagePart != null && imagePart.getSize() > 0;
        String nickname = InputValidator.trimmed(request.getParameter("nickname"));
        String email = InputValidator.trimmed(request.getParameter("email"));
        String phone = InputValidator.trimmed(request.getParameter("phone"));
        String bio = InputValidator.trimmed(request.getParameter("bio"));
        if (!InputValidator.hasLength(nickname, 1, 100)
                || !InputValidator.hasLength(email, 1, 150)
                || !InputValidator.hasLength(phone, 0, 30)
                || !InputValidator.hasLength(bio, 0, 1000)
                || !InputValidator.isEmail(email)
                || !InputValidator.isPhone(phone)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE,
                    "닉네임, 이메일, 전화번호 또는 소개의 형식과 길이를 확인하세요.");
            response.sendRedirect(request.getContextPath() + "/mypage/profile");
            return;
        }

        String savedName = null;
        String previousProfileImagePath = loginUser.getProfileImagePath();
        if (hasImage) {
            if (!FileValidationUtil.isAllowedProfileImage(imagePart)) {
                request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "프로필 이미지는 PNG, JPG, JPEG, GIF만 업로드할 수 있습니다.");
                response.sendRedirect(request.getContextPath() + "/mypage/profile");
                return;
            }

            String submittedFileName = FileValidationUtil.cleanOriginalName(imagePart.getSubmittedFileName());
            String extension = FileValidationUtil.extension(submittedFileName);
            savedName = UUID.randomUUID() + extension;
            Path target = FileValidationUtil.safeResolve(UploadPathUtil.getProfilesDirectory(), savedName);
            com.scuredweb.common.security.UploadBudget.checkSpace(target.getParent(), imagePart.getSize());
            try (var inputStream = imagePart.getInputStream()) {
                Files.copy(inputStream, target);
            } catch (IOException | RuntimeException exception) {
                deleteProfileFile(savedName);
                throw exception;
            }
        }

        boolean profileUpdated = userService.updateProfile(loginUser, nickname, email, phone, request.getParameter("currentPassword"));
        if (!profileUpdated) {
            if (savedName != null) {
                deleteProfileFile(savedName);
            }
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "프로필 수정에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/mypage/profile");
            return;
        }

        if (!SocialService.updateBio(loginUser, bio != null ? bio.trim() : "")) {
            if (savedName != null) {
                deleteProfileFile(savedName);
            }
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE,
                    "프로필 기본 정보는 저장되었지만 소개 저장에 실패했습니다. 다시 시도하세요.");
            response.sendRedirect(request.getContextPath() + "/mypage/profile");
            return;
        }

        if (savedName != null) {
            String publicPath = PROFILE_IMAGE_PREFIX + savedName;
            if (!userService.updateProfileImage(loginUser, publicPath)) {
                deleteProfileFile(savedName);
                request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "프로필 이미지 수정에 실패했습니다.");
                response.sendRedirect(request.getContextPath() + "/mypage/profile");
                return;
            }
            deletePreviousProfileFile(previousProfileImagePath);
        }

        request.getSession().setAttribute(SessionKey.LOGIN_USER, loginUser);
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "프로필 정보가 저장되었습니다.");
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

        request.getSession().invalidate();
        response.sendRedirect(request.getContextPath() + "/login");
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

    private void deletePreviousProfileFile(String profileImagePath) {
        String savedName = extractProfileSavedName(profileImagePath);
        if (savedName != null) {
            deleteProfileFile(savedName);
        }
    }

    private String extractProfileSavedName(String profileImagePath) {
        if (profileImagePath == null || profileImagePath.isBlank() || DEFAULT_PROFILE_IMAGE.equals(profileImagePath)) {
            return null;
        }
        int prefixIndex = profileImagePath.indexOf(PROFILE_IMAGE_PREFIX);
        if (prefixIndex < 0) {
            return null;
        }
        String savedName = profileImagePath.substring(prefixIndex + PROFILE_IMAGE_PREFIX.length());
        int querySeparator = savedName.indexOf('&');
        if (querySeparator >= 0) {
            savedName = savedName.substring(0, querySeparator);
        }
        savedName = Path.of(savedName).getFileName().toString();
        return savedName.isBlank() ? null : savedName;
    }

    private void deleteProfileFile(String savedName) {
        StoredFileCleanup.deleteProfile(savedName, "profile-update");
    }
}

