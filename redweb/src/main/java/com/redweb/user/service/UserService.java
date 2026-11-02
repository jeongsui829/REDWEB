package com.redweb.user.service;

import com.redweb.auth.service.AuthService;
import com.redweb.board.dto.PostDTO;
import com.redweb.comment.dto.CommentDTO;
import com.redweb.common.util.SampleDataFactory;
import com.redweb.notification.dto.NotificationDTO;
import com.redweb.social.service.SocialService;
import com.redweb.user.dao.UserDAO;
import com.redweb.user.dto.LoginHistoryDTO;
import com.redweb.user.dto.UserDTO;

import java.util.List;

public class UserService {
    private final UserDAO userDAO = new UserDAO();

    public UserDTO getUser(Long userId, UserDTO fallbackUser) {
        try {
            UserDTO user = userDAO.findById(userId);
            UserDTO resolved = user != null ? user : fallbackUser;
            SocialService.enrichUser(resolved);
            return resolved;
        } catch (Exception exception) {
            UserDTO resolved = fallbackUser != null ? fallbackUser : SampleDataFactory.user();
            SocialService.enrichUser(resolved);
            return resolved;
        }
    }

    public List<LoginHistoryDTO> getLoginHistory(Long userId) {
        try {
            return userDAO.findLoginHistory(userId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<NotificationDTO> getNotifications(Long userId) {
        return SocialService.getNotifications(userId);
    }

    public List<PostDTO> getMyPosts(Long userId) {
        try {
            return userDAO.findPostsByUserId(userId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<CommentDTO> getMyComments(Long userId) {
        try {
            return userDAO.findCommentsByUserId(userId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public boolean updateProfileImage(UserDTO loginUser, String profileImagePath) {
        if (loginUser == null || profileImagePath == null || profileImagePath.isBlank()) {
            return false;
        }

        loginUser.setProfileImagePath(profileImagePath);
        AuthService.syncLocalUser(loginUser);
        try {
            return userDAO.updateProfileImage(loginUser.getUserId(), profileImagePath);
        } catch (Exception ignored) {
            return true;
        }
    }

    public boolean updateProfile(UserDTO loginUser, String nickname, String email, String phone) {
        if (loginUser == null || loginUser.getUserId() == null) {
            return false;
        }

        String resolvedNickname = nickname == null || nickname.isBlank() ? loginUser.getNickname() : nickname.trim();
        String resolvedEmail = email == null || email.isBlank() ? loginUser.getEmail() : email.trim();
        String resolvedPhone = phone == null ? "" : phone.trim();

        try {
            boolean updated = userDAO.updateProfile(loginUser.getUserId(), resolvedNickname, resolvedEmail, resolvedPhone);
            if (!updated) {
                return false;
            }
        } catch (Exception exception) {
            return false;
        }

        loginUser.setNickname(resolvedNickname);
        loginUser.setEmail(resolvedEmail);
        loginUser.setPhone(resolvedPhone);
        AuthService.syncLocalUser(loginUser);
        return true;
    }

    public String changePassword(UserDTO loginUser, String currentPassword, String newPassword, String confirmPassword) {
        if (loginUser == null) {
            return "로그인 상태를 다시 확인해주세요.";
        }
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()) {
            return "모든 비밀번호 항목을 입력해주세요.";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "새 비밀번호와 확인 비밀번호가 일치하지 않습니다.";
        }
        if (newPassword.length() < 4) {
            return "새 비밀번호는 4자 이상으로 입력해주세요.";
        }
        if (loginUser.getPasswordHash() == null || !loginUser.getPasswordHash().equals(currentPassword)) {
            return "현재 비밀번호가 올바르지 않습니다.";
        }

        try {
            boolean updated = userDAO.updatePassword(loginUser.getUserId(), currentPassword, newPassword);
            if (updated) {
                loginUser.setPasswordHash(newPassword);
                AuthService.syncLocalUser(loginUser);
                return null;
            }
        } catch (Exception ignored) {
        }

        loginUser.setPasswordHash(newPassword);
        AuthService.syncLocalUser(loginUser);
        return null;
    }

    public boolean withdraw(UserDTO loginUser) {
        if (loginUser == null) {
            return false;
        }

        try {
            boolean deleted = userDAO.deleteUserHard(loginUser.getUserId());
            if (deleted) {
                AuthService.removeLocalUser(loginUser.getLoginId());
                return true;
            }
        } catch (Exception ignored) {
        }

        AuthService.removeLocalUser(loginUser.getLoginId());
        return false;
    }
}
