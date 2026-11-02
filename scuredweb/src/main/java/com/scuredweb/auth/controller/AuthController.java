package com.scuredweb.auth.controller;

import com.scuredweb.auth.dto.LoginRequestDTO;
import com.scuredweb.auth.dto.RegisterRequestDTO;
import com.scuredweb.auth.service.AccountRecoveryNotifier;
import com.scuredweb.auth.service.AccountRecoveryNotifierFactory;
import com.scuredweb.auth.service.AuthResult;
import com.scuredweb.auth.service.AuthService;
import com.scuredweb.auth.service.NoOpAccountRecoveryNotifier;
import com.scuredweb.common.config.AppConfig;
import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.security.AuthorizationUtil;
import com.scuredweb.common.security.RateLimitService;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(urlPatterns = {"/login", "/logout", "/register", "/find-id", "/find-password", "/verify-email", "/reset-password"})
public class AuthController extends HttpServlet {
    private static final String PENDING_RESET_TOKEN = "pendingResetToken";
    private static final String ACCOUNT_RECOVERY_MESSAGE =
            "If an account matches the submitted information, recovery instructions will be processed.";
    private static final String PASSWORD_RESET_NOTICE =
            "If an account matches the submitted information, password reset instructions will be processed.";
    private static final String RATE_LIMIT_MESSAGE =
            "Requests are temporarily limited. Please try again later.";
    private final AuthService authService = new AuthService();
    private final AccountRecoveryNotifier accountRecoveryNotifier = AccountRecoveryNotifierFactory.create();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (rejectDisabledRegistration(request, response) || rejectUnavailableRecovery(request, response)) return;
        if ("/logout".equals(request.getServletPath())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if ("/reset-password".equals(request.getServletPath()) && !prepareResetTokenView(request, response)) {
            if (!response.isCommitted()) {
                response.sendRedirect(request.getContextPath() + "/find-password");
            }
            return;
        }
        if ("/verify-email".equals(request.getServletPath())) {
            response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
        String view = switch (request.getServletPath()) {
            case "/register" -> "auth/register";
            case "/find-id" -> "auth/find-id";
            case "/find-password" -> "auth/find-password";
            case "/verify-email" -> "auth/verify-email";
            case "/reset-password" -> "auth/reset-password";
            default -> "auth/login";
        };
        ViewUtil.forward(request, response, view);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (rejectDisabledRegistration(request, response) || rejectUnavailableRecovery(request, response)) return;
        switch (request.getServletPath()) {
            case "/register" -> handleRegister(request, response);
            case "/find-id" -> handleFindId(request, response);
            case "/find-password" -> handleFindPassword(request, response);
            case "/verify-email" -> handleUnsupportedEmailVerification(request, response);
            case "/reset-password" -> handleResetPassword(request, response);
            case "/logout" -> handleLogout(request, response);
            default -> handleLogin(request, response);
        }
    }

    private boolean rejectUnavailableRecovery(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String p = request.getServletPath();
        if (AppConfig.production() && accountRecoveryNotifier instanceof NoOpAccountRecoveryNotifier
                && ("/find-id".equals(p) || "/find-password".equals(p) || "/reset-password".equals(p) || "/verify-email".equals(p))) {
            response.sendError(503, "계정 복구 및 이메일 인증은 현재 제공되지 않습니다. 관리자에게 문의하세요.");
            return true;
        }
        return false;
    }

    private boolean rejectDisabledRegistration(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/register".equals(request.getServletPath()) && !AppConfig.publicRegistrationEnabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "현재 공개 회원가입을 받지 않습니다.");
            return true;
        }
        return false;
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginId = request.getParameter("loginId");
        String password = request.getParameter("password");
        String clientIp = request.getRemoteAddr();
        if (!RateLimitService.consumeAuthentication("login", clientIp, loginId) || RateLimitService.isBlocked("login", clientIp, loginId)) {
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE, RATE_LIMIT_MESSAGE);
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        LoginRequestDTO requestDTO = new LoginRequestDTO();
        requestDTO.setLoginId(loginId);
        requestDTO.setPassword(password);
        UserDTO loginUser = authService.login(requestDTO, clientIp, request.getHeader("User-Agent"));
        if (loginUser == null) {
            RateLimitService.recordFailure("login", clientIp, loginId);
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE, authService.resolveLoginFailureMessage(loginId));
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        RateLimitService.recordSuccess("login", clientIp, loginId);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SessionKey.LOGIN_USER, loginUser);
        session.removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED);
        session.removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED_AT);
        if (AuthorizationUtil.isCurrentAdmin(loginUser)) {
            session.setAttribute(SessionKey.ADMIN_USER, loginUser);
        } else {
            session.removeAttribute(SessionKey.ADMIN_USER);
        }
        response.sendRedirect(request.getContextPath() + "/home");
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/home");
    }

    private void handleRegister(HttpServletRequest request, HttpServletResponse response) throws IOException {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO();
        requestDTO.setLoginId(request.getParameter("loginId"));
        requestDTO.setUserName(request.getParameter("userName"));
        requestDTO.setNickname(request.getParameter("nickname"));
        requestDTO.setEmail(request.getParameter("email"));
        requestDTO.setPhone(request.getParameter("phone"));
        requestDTO.setPassword(request.getParameter("password"));
        requestDTO.setSecurityQuestion(request.getParameter("securityQuestion"));
        requestDTO.setSecurityAnswer(request.getParameter("securityAnswer"));

        AuthResult result = authService.register(requestDTO);
        if (!result.isSuccess()) {
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE, result.getMessage());
            response.sendRedirect(request.getContextPath() + "/register");
            return;
        }
        request.getSession(true).setAttribute(MessageKey.FLASH_MESSAGE, result.getMessage());
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private void handleFindId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (accountRecoveryNotifier instanceof NoOpAccountRecoveryNotifier) {
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE,
                    "계정 복구 알림 전송 기능은 현재 지원되지 않습니다.");
            response.sendRedirect(request.getContextPath() + "/find-id");
            return;
        }
        String userName = request.getParameter("userName");
        String email = request.getParameter("email");
        String clientIp = request.getRemoteAddr();
        String rateLimitKey = email == null || email.isBlank() ? userName : email;
        if (RateLimitService.isBlocked("find-id", clientIp, rateLimitKey)) {
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE, RATE_LIMIT_MESSAGE);
            response.sendRedirect(request.getContextPath() + "/find-id");
            return;
        }

        String loginId = authService.findLoginId(userName, email);
        accountRecoveryNotifier.sendFindIdNotice(userName, email, loginId);
        RateLimitService.recordFailure("find-id", clientIp, rateLimitKey);
        request.getSession(true).setAttribute(MessageKey.FLASH_MESSAGE, ACCOUNT_RECOVERY_MESSAGE);
        response.sendRedirect(request.getContextPath() + "/find-id");
    }

    private void handleFindPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (accountRecoveryNotifier instanceof NoOpAccountRecoveryNotifier) {
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE,
                    "비밀번호 복구 알림 전송 기능은 현재 지원되지 않습니다.");
            response.sendRedirect(request.getContextPath() + "/find-password");
            return;
        }
        String loginId = request.getParameter("loginId");
        String email = request.getParameter("email");
        String clientIp = request.getRemoteAddr();
        if (RateLimitService.isBlocked("find-password", clientIp, loginId)) {
            request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE, RATE_LIMIT_MESSAGE);
            response.sendRedirect(request.getContextPath() + "/find-password");
            return;
        }
        String token = authService.createPasswordResetToken(loginId, email);
        RateLimitService.recordFailure("find-password", clientIp, loginId);
        if (token != null) {
            String resetUrl = buildPasswordResetUrl(request, token);
            accountRecoveryNotifier.sendPasswordResetNotice(loginId, email, resetUrl);
        }
        request.getSession(true).setAttribute(MessageKey.FLASH_MESSAGE, PASSWORD_RESET_NOTICE);
        response.sendRedirect(request.getContextPath() + "/find-password");
    }

    private void handleUnsupportedEmailVerification(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.getSession(true).setAttribute(MessageKey.ERROR_MESSAGE,
                "이메일 인증 기능은 현재 지원되지 않습니다.");
        response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Email verification is not configured.");
    }

    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/find-password");
            return;
        }
        String token = (String) session.getAttribute(PENDING_RESET_TOKEN);
        if (!authService.isPasswordResetTokenValid(token)) {
            clearPendingResetToken(session);
            session.setAttribute(MessageKey.ERROR_MESSAGE, "비밀번호 재설정 요청이 잘못되었거나 만료되었습니다.");
            response.sendRedirect(request.getContextPath() + "/find-password");
            return;
        }
        String clientIp = request.getRemoteAddr();
        if (RateLimitService.isBlocked("reset-password", clientIp, clientIp)) {
            session.setAttribute(MessageKey.ERROR_MESSAGE, RATE_LIMIT_MESSAGE);
            response.sendRedirect(request.getContextPath() + "/reset-password");
            return;
        }

        AuthResult result = authService.resetPasswordWithToken(
                token,
                request.getParameter("newPassword"),
                request.getParameter("confirmPassword"));
        if (!result.isSuccess()) {
            RateLimitService.recordFailure("reset-password", clientIp, clientIp);
            if ("비밀번호 재설정 요청이 잘못되었거나 만료되었습니다.".equals(result.getMessage())) {
                clearPendingResetToken(session);
                session.setAttribute(MessageKey.ERROR_MESSAGE, result.getMessage());
                response.sendRedirect(request.getContextPath() + "/find-password");
                return;
            }
            session.setAttribute(MessageKey.ERROR_MESSAGE, result.getMessage());
            response.sendRedirect(request.getContextPath() + "/reset-password");
            return;
        }
        RateLimitService.recordSuccess("reset-password", clientIp, clientIp);
        clearPendingResetToken(session);
        session.setAttribute(MessageKey.FLASH_MESSAGE, result.getMessage());
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private boolean prepareResetTokenView(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clientIp = request.getRemoteAddr();
        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            if (RateLimitService.isBlocked("reset-token-check", clientIp, clientIp)) {
                response.sendRedirect(request.getContextPath() + "/find-password?reset=limited");
                return false;
            }
            if (!authService.isPasswordResetTokenValid(token)) {
                RateLimitService.recordFailure("reset-token-check", clientIp, clientIp);
                HttpSession existingSession = request.getSession(false);
                clearPendingResetToken(existingSession);
                response.sendRedirect(request.getContextPath() + "/find-password?reset=invalid");
                return false;
            }
            RateLimitService.recordSuccess("reset-token-check", clientIp, clientIp);
            HttpSession session = request.getSession(true);
            session.setAttribute(PENDING_RESET_TOKEN, token);
            response.sendRedirect(request.getContextPath() + "/reset-password");
            return false;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/find-password");
            return false;
        }

        Object pendingToken = session.getAttribute(PENDING_RESET_TOKEN);
        if (!(pendingToken instanceof String pending) || !authService.isPasswordResetTokenValid(pending)) {
            clearPendingResetToken(session);
            response.sendRedirect(request.getContextPath() + "/find-password?reset=invalid");
            return false;
        }
        return true;
    }

    private String buildPasswordResetUrl(HttpServletRequest request, String token) {
        String resetPath = "/reset-password" + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String publicBaseUrl = AppConfig.publicBaseUrl();
        return publicBaseUrl == null ? request.getContextPath() + resetPath : publicBaseUrl + resetPath;
    }

    private void clearPendingResetToken(HttpSession session) {
        if (session != null) {
            session.removeAttribute(PENDING_RESET_TOKEN);
        }
    }
}

