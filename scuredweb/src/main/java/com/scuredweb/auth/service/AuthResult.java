package com.scuredweb.auth.service;

import com.scuredweb.user.dto.UserDTO;

public class AuthResult {
    private final boolean success;
    private final String message;
    private final UserDTO user;

    private AuthResult(boolean success, String message, UserDTO user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public static AuthResult success(String message, UserDTO user) {
        return new AuthResult(true, message, user);
    }

    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public UserDTO getUser() {
        return user;
    }
}

