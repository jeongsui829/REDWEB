package com.scuredweb.common.security;

import com.scuredweb.common.constant.RoleCode;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;

public final class AuthorizationUtil {
    private AuthorizationUtil() {
    }

    public static boolean hasFreshVerification(jakarta.servlet.http.HttpSession session) {
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(
                com.scuredweb.common.constant.SessionKey.ADMIN_ACCESS_VERIFIED))) return false;
        Object value = session.getAttribute(com.scuredweb.common.constant.SessionKey.ADMIN_ACCESS_VERIFIED_AT);
        long now = System.currentTimeMillis();
        return value instanceof Long at && at <= now && now - at <= 600_000L;
    }

    public static boolean isVerifiedAdmin(jakarta.servlet.http.HttpSession session, UserDTO user) {
        return hasFreshVerification(session) && isCurrentAdmin(user);
    }

    public static boolean isCurrentAdmin(UserDTO loginUser) {
        if (loginUser == null || loginUser.getUserId() == null) {
            return false;
        }
        try {
            UserDTO current = new UserDAO().findById(loginUser.getUserId());
            return current != null
                    && current.getAuthVersion() == loginUser.getAuthVersion()
                    && "ACTIVE".equals(current.getStatus())
                    && (RoleCode.ADMIN.equals(current.getRoleCode())
                    || RoleCode.SUPER_ADMIN.equals(current.getRoleCode()));
        } catch (Exception exception) {
            return false;
        }
    }
}

