package com.scuredweb.user.service;

import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.comment.dto.CommentDTO;
import com.scuredweb.common.security.PasswordUtil;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.StoredFileCleanup;
import com.scuredweb.notification.dto.NotificationDTO;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.LoginHistoryDTO;
import com.scuredweb.user.dto.UserDTO;

import java.util.List;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserService {
    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());
    private final UserDAO userDAO = new UserDAO();

    public UserDTO getUser(Long userId) {
        try {
            UserDTO user = userDAO.findById(userId);
            if (user == null) {
                throw new DataAccessException("회원 정보를 찾을 수 없습니다.", null);
            }
            SocialService.enrichUser(user);
            return user;
        } catch (Exception exception) {
            if (exception instanceof DataAccessException dataAccessException) {
                throw dataAccessException;
            }
            throw new DataAccessException("회원 정보를 불러오지 못했습니다.", exception);
        }
    }

    public List<LoginHistoryDTO> getLoginHistory(Long userId) {
        try {
            return userDAO.findLoginHistory(userId);
        } catch (Exception exception) {
            throw new DataAccessException("로그인 기록을 불러오지 못했습니다.", exception);
        }
    }

    public List<NotificationDTO> getNotifications(Long userId) {
        return SocialService.getNotifications(userId);
    }

    public List<PostDTO> getMyPosts(Long userId) {
        try {
            return userDAO.findPostsByUserId(userId);
        } catch (Exception exception) {
            throw new DataAccessException("작성한 게시글을 불러오지 못했습니다.", exception);
        }
    }

    public List<CommentDTO> getMyComments(Long userId) {
        try {
            return userDAO.findCommentsByUserId(userId);
        } catch (Exception exception) {
            throw new DataAccessException("작성한 댓글을 불러오지 못했습니다.", exception);
        }
    }

    public boolean updateProfileImage(UserDTO loginUser, String profileImagePath) {
        if (loginUser == null || profileImagePath == null || profileImagePath.isBlank()) {
            return false;
        }

        try {
            boolean updated = userDAO.updateProfileImage(loginUser.getUserId(), profileImagePath);
            if (updated) {
                loginUser.setProfileImagePath(profileImagePath);
            }
            return updated;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Profile image update failed. userId=" + loginUser.getUserId(), exception);
            return false;
        }
    }

    public boolean existsProfileImageSavedName(String savedName) {
        if (savedName == null || savedName.isBlank()) {
            return false;
        }
        try {
            return userDAO.existsProfileImageSavedName(savedName);
        } catch (Exception exception) {
            throw new DataAccessException("프로필 이미지 정보를 확인하지 못했습니다.", exception);
        }
    }

    public boolean updateProfile(UserDTO loginUser, String nickname, String email, String phone) {
        return updateProfile(loginUser, nickname, email, phone, null);
    }
    public boolean updateProfile(UserDTO loginUser, String nickname, String email, String phone, String password) {
        if (loginUser == null || loginUser.getUserId() == null) {
            return false;
        }

        String resolvedNickname = nickname == null || nickname.isBlank() ? loginUser.getNickname() : nickname.trim();
        String resolvedEmail = email == null || email.isBlank() ? loginUser.getEmail() : email.trim();
        String resolvedPhone = phone == null ? "" : phone.trim();

        try {
            boolean updated = userDAO.updateProfile(loginUser.getUserId(), resolvedNickname, resolvedEmail, resolvedPhone, password);
            if (!updated) {
                return false;
            }
        } catch (Exception exception) {
            return false;
        }

        loginUser.setNickname(resolvedNickname);
        if (!java.util.Objects.equals(loginUser.getEmail(), resolvedEmail)) loginUser.setEmailVerified(false);
        loginUser.setEmail(resolvedEmail);
        loginUser.setPhone(resolvedPhone);
        return true;
    }

    public String changePassword(UserDTO loginUser, String currentPassword, String newPassword, String confirmPassword) {
        if (loginUser == null) {
            return "로그인 세션이 유효하지 않습니다.";
        }
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()) {
            return "비밀번호 항목을 모두 입력하세요.";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "새 비밀번호와 확인 값이 일치하지 않습니다.";
        }
        if (!PasswordUtil.isNewPasswordAllowed(newPassword)) {
            return "새 비밀번호는 15자 이상 128자 이하여야 합니다.";
        }
        if (!PasswordUtil.verifyPassword(currentPassword, loginUser.getPasswordHash())) {
            return "현재 비밀번호가 올바르지 않습니다.";
        }

        try {
            String newPasswordHash = PasswordUtil.hash(newPassword);
            boolean updated = userDAO.updatePassword(loginUser.getUserId(), loginUser.getPasswordHash(), newPasswordHash);
            if (updated) {
                loginUser.setPasswordHash(newPasswordHash);
                return null;
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Password change failed. userId=" + loginUser.getUserId(), exception);
        }

        return "비밀번호 변경에 실패했습니다.";
    }

    public boolean withdraw(UserDTO loginUser, String currentPassword) {
        if (loginUser == null || !"MEMBER".equals(loginUser.getRoleCode()) || currentPassword == null || currentPassword.isBlank()) return false;
        try {
            List<String> names = userDAO.findAttachmentSavedNamesForUserDeletion(loginUser.getUserId());
            if (!userDAO.withdrawUser(loginUser.getUserId(), currentPassword)) return false;
            StoredFileCleanup.deleteAttachments(names, "user-withdraw:" + loginUser.getUserId());
            StoredFileCleanup.deleteProfile(profileSavedName(loginUser.getProfileImagePath()), "user-withdraw:" + loginUser.getUserId());
            return true;
        } catch (Exception e) { LOGGER.warning("Withdrawal failed"); return false; }
    }

    /** Trusted internal/offline compatibility helper, never an HTTP withdrawal entry point. DAO enforces MEMBER. */
    @Deprecated
    public boolean withdraw(UserDTO loginUser) {
        if (com.scuredweb.common.config.AppConfig.production()) return false;
        if (loginUser == null) {
            return false;
        }

        try {
            List<String> attachmentNames = userDAO.findAttachmentSavedNamesForUserDeletion(loginUser.getUserId());
            String profileSavedName = profileSavedName(loginUser.getProfileImagePath());
            boolean deleted = userDAO.withdrawUser(loginUser.getUserId());
            if (deleted) {
                StoredFileCleanup.deleteAttachments(attachmentNames, "user-withdraw:" + loginUser.getUserId());
                StoredFileCleanup.deleteProfile(profileSavedName, "user-withdraw:" + loginUser.getUserId());
                return true;
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "User withdrawal failed. userId=" + loginUser.getUserId(), exception);
        }

        return false;
    }

    private String profileSavedName(String profileImagePath) {
        if (profileImagePath == null || !profileImagePath.startsWith("/profile/image?savedName=")) {
            return null;
        }
        String value = profileImagePath.substring("/profile/image?savedName=".length());
        int separator = value.indexOf('&');
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        return value.isBlank() ? null : Path.of(value).getFileName().toString();
    }
}

