package com.scuredweb.auth.service;

import com.scuredweb.auth.dao.AuthDAO;
import com.scuredweb.auth.dto.LoginRequestDTO;
import com.scuredweb.auth.dto.RegisterRequestDTO;
import com.scuredweb.common.security.PasswordUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.user.dto.UserDTO;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;
    private static final int RESET_TOKEN_TTL_MINUTES = 10;
    private static final String GENERIC_LOGIN_FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";
    private final AuthDAO authDAO = new AuthDAO();

    public UserDTO login(LoginRequestDTO requestDTO) {
        return login(requestDTO, null, null);
    }

    public UserDTO login(LoginRequestDTO requestDTO, String loginIp, String userAgent) {
        if (requestDTO == null || !PasswordUtil.isAllowedPassword(requestDTO.getPassword())) return null;
        try {
            authDAO.releaseExpiredSuspensionByLoginId(requestDTO.getLoginId());
            UserDTO user = authDAO.findUserForLogin(requestDTO);
            if (user == null || !"ACTIVE".equals(user.getStatus()) || !PasswordUtil.verifyPassword(requestDTO.getPassword(), user.getPasswordHash())) {
                return null;
            }
            if (PasswordUtil.needsRehash(user.getPasswordHash())) {
                String migratedHash = PasswordUtil.hash(requestDTO.getPassword());
                if (!PasswordUtil.isHash(user.getPasswordHash())) authDAO.updatePasswordHash(user.getUserId(), migratedHash);
                else if (!authDAO.upgradePasswordHash(user.getUserId(), user.getPasswordHash(), migratedHash)) return null;
                user.setPasswordHash(migratedHash);
            }
            authDAO.recordSuccessfulLogin(user.getUserId(), loginIp, userAgent);
            user.setLastLoginAt(LocalDateTime.now());
            return user;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Login processing failed", exception);
            return null;
        }
    }

    public String resolveLoginFailureMessage(String loginId) {
        return GENERIC_LOGIN_FAILURE_MESSAGE;
    }

    public AuthResult register(RegisterRequestDTO requestDTO) {
        if (!com.scuredweb.common.config.AppConfig.publicRegistrationEnabled()) {
            return AuthResult.failure("현재 공개 회원가입을 받지 않습니다.");
        }
        if (isBlank(requestDTO.getLoginId())
                || isBlank(requestDTO.getUserName())
                || isBlank(requestDTO.getNickname())
                || isBlank(requestDTO.getEmail())
                || isBlank(requestDTO.getPassword())
                || isBlank(requestDTO.getSecurityQuestion())
                || isBlank(requestDTO.getSecurityAnswer())) {
            return AuthResult.failure("필수 항목을 모두 입력하세요.");
        }
        String normalizedPhone = requestDTO.getPhone() == null ? null : requestDTO.getPhone().trim();
        requestDTO.setPhone(normalizedPhone == null || normalizedPhone.isEmpty() ? null : normalizedPhone);
        if (!PasswordUtil.isNewPasswordAllowed(requestDTO.getPassword())) {
            return AuthResult.failure("비밀번호는 15자 이상 128자 이하여야 합니다.");
        }
        if (!InputValidator.hasLength(requestDTO.getLoginId(), 1, 50)
                || !InputValidator.hasLength(requestDTO.getUserName(), 1, 100)
                || !InputValidator.hasLength(requestDTO.getNickname(), 1, 100)
                || !InputValidator.hasLength(requestDTO.getEmail(), 1, 150)
                || !InputValidator.hasLength(requestDTO.getSecurityQuestion(), 1, 300)
                || !InputValidator.hasLength(requestDTO.getSecurityAnswer(), 1, 300)
                || !InputValidator.isEmail(requestDTO.getEmail())
                || (requestDTO.getPhone() != null
                && (!InputValidator.hasLength(requestDTO.getPhone(), 1, 30)
                || !InputValidator.isPhone(requestDTO.getPhone())))) {
            return AuthResult.failure("회원가입 입력값의 형식과 길이를 확인하세요.");
        }

        try {
            if (authDAO.existsConflict(requestDTO.getLoginId(), requestDTO.getNickname(), requestDTO.getEmail())) {
                return AuthResult.failure("아이디, 닉네임 또는 이메일이 이미 사용 중입니다.");
            }
            requestDTO.setPassword(PasswordUtil.hash(requestDTO.getPassword()));
            requestDTO.setSecurityAnswer(PasswordUtil.hash(normalizeSecurityAnswer(requestDTO.getSecurityAnswer())));
            UserDTO createdUser = authDAO.insertUser(requestDTO);
            return AuthResult.success("회원가입이 완료되었습니다. 로그인해주세요.", createdUser);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Registration failed", exception);
            return AuthResult.failure("회원가입 처리 중 오류가 발생했습니다. 잠시 후 다시 시도하세요.");
        }
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

    public String createPasswordResetToken(String loginId, String email) {
        if (isBlank(loginId) || isBlank(email)) {
            return null;
        }
        try {
            Long userId = authDAO.findActiveUserId(loginId.trim(), email.trim());
            if (userId == null) {
                return null;
            }
            String token = generateResetToken();
            authDAO.replacePasswordResetToken(userId, hashToken(token), LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
            return token;
        } catch (Exception exception) {
            return null;
        }
    }

    public boolean isPasswordResetTokenValid(String token) {
        if (isBlank(token)) {
            return false;
        }
        try {
            return authDAO.existsValidPasswordResetToken(hashToken(token));
        } catch (Exception exception) {
            return false;
        }
    }

    public AuthResult resetPasswordWithToken(String token, String newPassword, String confirmPassword) {
        if (isBlank(token) || isBlank(newPassword) || isBlank(confirmPassword)) {
            return AuthResult.failure("비밀번호 재설정 요청이 잘못되었거나 만료되었습니다.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return AuthResult.failure("새 비밀번호와 확인 값이 일치하지 않습니다.");
        }
        if (!PasswordUtil.isNewPasswordAllowed(newPassword)) {
            return AuthResult.failure("비밀번호는 15자 이상 128자 이하여야 합니다.");
        }
        try {
            boolean updated = authDAO.resetPasswordWithToken(hashToken(token), PasswordUtil.hash(newPassword));
            return updated
                    ? AuthResult.success("비밀번호가 변경되었습니다. 다시 로그인해주세요.", null)
                    : AuthResult.failure("비밀번호 재설정 요청이 잘못되었거나 만료되었습니다.");
        } catch (Exception exception) {
            return AuthResult.failure("비밀번호 재설정 요청이 잘못되었거나 만료되었습니다.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeSecurityAnswer(String answer) {
        return normalize(answer);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String generateResetToken() {
        byte[] token = new byte[RESET_TOKEN_BYTES];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Reset token hashing is not available", exception);
        }
    }


}
