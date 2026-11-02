package com.redweb.auth.controller;

import com.redweb.auth.dto.LoginRequestDTO;
import com.redweb.auth.dto.RegisterRequestDTO;
import com.redweb.auth.service.AuthResult;
import com.redweb.auth.service.AuthService;
import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.RoleCode;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.ViewUtil;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/login", "/logout", "/register", "/find-id", "/find-password", "/verify-email", "/reset-password"})
public class AuthController extends HttpServlet {
    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();
        String view = switch (servletPath) {
            case "/register" -> "auth/register";
            case "/find-id" -> "auth/find-id";
            case "/find-password" -> "auth/find-password";
            case "/verify-email" -> "auth/verify-email";
            case "/reset-password" -> "auth/reset-password";
            case "/logout" -> null;
            default -> "auth/login";
        };

        if ("/logout".equals(servletPath)) {
            request.getSession().invalidate();
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        ViewUtil.forward(request, response, view);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String servletPath = request.getServletPath();
        if ("/register".equals(servletPath)) {
            handleRegister(request, response);
            return;
        }
        if ("/find-id".equals(servletPath)) {
            handleFindId(request, response);
            return;
        }
        if ("/find-password".equals(servletPath)) {
            handleFindPassword(request, response);
            return;
        }
        if ("/reset-password".equals(servletPath)) {
            handleResetPassword(request, response);
            return;
        }

        String loginId = request.getParameter("loginId");
        String password = request.getParameter("password");

        LoginRequestDTO requestDTO = new LoginRequestDTO();
        requestDTO.setLoginId(loginId);
        requestDTO.setPassword(password);

        UserDTO loginUser = authService.login(requestDTO);
        if (loginUser == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, authService.resolveLoginFailureMessage(loginId));
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        AuthService.syncLocalUser(loginUser);
        request.getSession().setAttribute(SessionKey.LOGIN_USER, loginUser);
        request.getSession().removeAttribute(SessionKey.ADMIN_ACCESS_VERIFIED);
        if (RoleCode.ADMIN.equals(loginUser.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(loginUser.getRoleCode())) {
            request.getSession().setAttribute(SessionKey.ADMIN_USER, loginUser);
        } else {
            request.getSession().removeAttribute(SessionKey.ADMIN_USER);
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
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, result.getMessage());
            response.sendRedirect(request.getContextPath() + "/register");
            return;
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, result.getMessage());
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private void handleFindId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userName = request.getParameter("userName");
        String email = request.getParameter("email");
        String loginId = authService.findLoginId(userName, email);
        request.getSession().setAttribute(loginId == null ? MessageKey.ERROR_MESSAGE : MessageKey.FLASH_MESSAGE,
                loginId == null ? "일치하는 계정을 찾지 못했습니다." : "회원님의 아이디는 " + loginId + " 입니다.");
        response.sendRedirect(request.getContextPath() + "/find-id");
    }

    private void handleFindPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginId = request.getParameter("loginId");
        String email = request.getParameter("email");
        String securityQuestion = authService.findSecurityQuestion(loginId, email);
        if (securityQuestion == null) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "일치하는 계정을 찾지 못했습니다.");
            response.sendRedirect(request.getContextPath() + "/find-password");
            return;
        }
        request.getSession().setAttribute("resetLoginId", loginId);
        request.getSession().setAttribute("resetEmail", email);
        request.getSession().setAttribute("resetQuestion", securityQuestion);
        response.sendRedirect(request.getContextPath() + "/reset-password");
    }

    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginId = (String) request.getSession().getAttribute("resetLoginId");
        String email = (String) request.getSession().getAttribute("resetEmail");
        String answer = request.getParameter("securityAnswer");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        AuthResult result = authService.resetPassword(loginId, email, answer, newPassword, confirmPassword);
        if (!result.isSuccess()) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, result.getMessage());
            response.sendRedirect(request.getContextPath() + "/reset-password");
            return;
        }
        request.getSession().removeAttribute("resetLoginId");
        request.getSession().removeAttribute("resetEmail");
        request.getSession().removeAttribute("resetQuestion");
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, result.getMessage());
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
