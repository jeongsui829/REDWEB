package com.redweb.auth.service;

import com.redweb.auth.dao.AuthDAO;
import com.redweb.auth.dto.LoginRequestDTO;
import com.redweb.auth.dto.RegisterRequestDTO;
import com.redweb.common.util.SampleDataFactory;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.UserDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final AuthDAO authDAO = new AuthDAO();
    private static final Map<String, UserDTO> LOCAL_USERS = new ConcurrentHashMap<>();

    static {
        registerPredefinedUser(SampleDataFactory.user());
        registerPredefinedUser(SampleDataFactory.memberOne());
        registerPredefinedUser(SampleDataFactory.memberTwo());
    }

    public UserDTO login(LoginRequestDTO requestDTO) {
        try {
            UserDTO user = authDAO.findUserForLogin(requestDTO);
            if (user == null) {
                return null;
            }
            if (!SocialService.releaseExpiredSuspension(user) && SocialService.isSuspended(user.getUserId())) {
                return null;
            }
            AuthService.syncLocalUser(user);
            return user;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String resolveLoginFailureMessage(String loginId) {
        try {
            UserDTO knownUser = authDAO.findUserByLoginId(loginId);
            if (knownUser != null
                    && !SocialService.releaseExpiredSuspension(knownUser)
                    && SocialService.isSuspended(knownUser.getUserId())) {
                return "정지된 계정입니다. 잠시 후 다시 시도해 주세요.";
            }
        } catch (Exception ignored) {
        }

        UserDTO localUser = LOCAL_USERS.get(normalize(loginId));
        if (localUser != null
                && !SocialService.releaseExpiredSuspension(localUser)
                && SocialService.isSuspended(localUser.getUserId())) {
            return "정지된 계정입니다. 잠시 후 다시 시도해 주세요.";
        }

        return "아이디 또는 비밀번호를 다시 확인해 주세요.";
    }

    public AuthResult register(RegisterRequestDTO requestDTO) {
        if (isBlank(requestDTO.getLoginId())
                || isBlank(requestDTO.getUserName())
                || isBlank(requestDTO.getNickname())
                || isBlank(requestDTO.getEmail())
                || isBlank(requestDTO.getPhone())
                || isBlank(requestDTO.getPassword())
                || isBlank(requestDTO.getSecurityQuestion())
                || isBlank(requestDTO.getSecurityAnswer())) {
            return AuthResult.failure("모든 항목을 입력해 주세요.");
        }

        if (requestDTO.getPassword().trim().length() < 8) {
            return AuthResult.failure("비밀번호는 8자 이상으로 입력해 주세요.");
        }

        String loginIdKey = normalize(requestDTO.getLoginId());
        try {
            if (authDAO.existsConflict(requestDTO.getLoginId(), requestDTO.getNickname(), requestDTO.getEmail())) {
                return AuthResult.failure("이미 사용 중인 아이디, 닉네임 또는 이메일입니다.");
            }

            UserDTO createdUser = authDAO.insertUser(requestDTO);
            LOCAL_USERS.put(loginIdKey, createdUser);
            return AuthResult.success("회원가입이 완료되었습니다. 로그인해 주세요.", createdUser);
        } catch (Exception exception) {
            return AuthResult.failure("DB 저장에 실패했습니다. Oracle 연결과 RW_USER 테이블을 확인해 주세요.");
        }
    }

    public boolean withdraw(UserDTO user) {
        if (user == null) {
            return false;
        }

        LOCAL_USERS.remove(normalize(user.getLoginId()));
        return true;
    }

    public String findLoginId(String userName, String email) {
        if (isBlank(userName) || isBlank(email)) {
            return null;
        }
        try {
            return authDAO.findLoginId(userName.trim(), email.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    public String findSecurityQuestion(String loginId, String email) {
        if (isBlank(loginId) || isBlank(email)) {
            return null;
        }
        try {
            return authDAO.findSecurityQuestion(loginId.trim(), email.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    public AuthResult resetPassword(String loginId, String email, String answer, String newPassword, String confirmPassword) {
        if (isBlank(loginId) || isBlank(email) || isBlank(answer) || isBlank(newPassword) || isBlank(confirmPassword)) {
            return AuthResult.failure("모든 항목을 입력해 주세요.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return AuthResult.failure("새 비밀번호와 확인 값이 일치하지 않습니다.");
        }
        if (newPassword.length() < 8) {
            return AuthResult.failure("비밀번호는 8자 이상으로 입력해 주세요.");
        }
        try {
            boolean updated = authDAO.resetPassword(loginId.trim(), email.trim(), answer.trim(), newPassword);
            return updated
                    ? AuthResult.success("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.", null)
                    : AuthResult.failure("보안 답변이 일치하지 않습니다.");
        } catch (Exception exception) {
            return AuthResult.failure("DB 처리 중 문제가 발생했습니다.");
        }
    }

    public static void removeLocalUser(String loginId) {
        LOCAL_USERS.remove(normalize(loginId));
    }

    public static void syncLocalUser(UserDTO user) {
        if (user == null || user.getLoginId() == null) {
            return;
        }
        LOCAL_USERS.put(normalize(user.getLoginId()), user);
    }

    public static List<UserDTO> getLocalUsers() {
        return new ArrayList<>(LOCAL_USERS.values());
    }

    public static boolean updateLocalUserStatus(Long userId, String status) {
        if (userId == null || status == null) {
            return false;
        }

        for (UserDTO user : LOCAL_USERS.values()) {
            if (userId.equals(user.getUserId())) {
                user.setStatus(status);
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void registerPredefinedUser(UserDTO user) {
        if (user == null || user.getLoginId() == null) {
            return;
        }
        LOCAL_USERS.put(normalize(user.getLoginId()), user);
    }
}
