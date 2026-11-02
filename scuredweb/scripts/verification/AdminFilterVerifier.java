package com.scuredweb.common.filter;

import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class AdminFilterVerifier {
    private AdminFilterVerifier() {
    }

    public static void main(String[] args) throws Exception {
        verifyDatabaseFailure();
        verifyDeletedAdminInvalidatesSession();
        verifySuspendedAdminInvalidatesSession();
        verifyWithdrawnSuperAdminInvalidatesSession();
        verifyDemotionClearsAdminState();
        verifyActiveAdminUsesOneLookup();
        System.out.println("AdminFilter verification passed");
    }

    private static void verifyDatabaseFailure() throws Exception {
        CountingUserDAO dao = new CountingUserDAO(null, true);
        Exchange exchange = exchange(admin("ADMIN", "ACTIVE"), true);
        new AdminFilter(dao).doFilter(exchange.request, exchange.response, exchange.chain);
        require(dao.calls == 1, "database failure lookup count");
        require(exchange.status == HttpServletResponse.SC_SERVICE_UNAVAILABLE, "database failure status");
        require(exchange.chainCalls == 0, "database failure chain");
    }

    private static void verifyDeletedAdminInvalidatesSession() throws Exception {
        CountingUserDAO dao = new CountingUserDAO(null, false);
        Exchange exchange = exchange(admin("ADMIN", "ACTIVE"), true);
        new AdminFilter(dao).doFilter(exchange.request, exchange.response, exchange.chain);
        require(dao.calls == 1, "deleted admin lookup count");
        require(exchange.status == HttpServletResponse.SC_FORBIDDEN, "deleted admin status");
        require(exchange.chainCalls == 0, "deleted admin chain");
        require(exchange.invalidated, "deleted admin session invalidation");
        require(exchange.attributes.isEmpty(), "deleted admin session cleanup");
    }

    private static void verifySuspendedAdminInvalidatesSession() throws Exception {
        CountingUserDAO dao = new CountingUserDAO(admin("ADMIN", "SUSPENDED"), false);
        Exchange exchange = exchange(admin("ADMIN", "ACTIVE"), true);
        new AdminFilter(dao).doFilter(exchange.request, exchange.response, exchange.chain);
        require(dao.calls == 1, "suspended admin lookup count");
        require(exchange.status == HttpServletResponse.SC_FORBIDDEN, "suspended admin status");
        require(exchange.chainCalls == 0, "suspended admin chain");
        require(exchange.invalidated, "suspended admin session invalidation");
        require(exchange.attributes.isEmpty(), "suspended admin session cleanup");
    }

    private static void verifyWithdrawnSuperAdminInvalidatesSession() throws Exception {
        CountingUserDAO dao = new CountingUserDAO(admin("SUPER_ADMIN", "WITHDRAWN"), false);
        Exchange exchange = exchange(admin("SUPER_ADMIN", "ACTIVE"), true);
        new AdminFilter(dao).doFilter(exchange.request, exchange.response, exchange.chain);
        require(dao.calls == 1, "withdrawn super admin lookup count");
        require(exchange.status == HttpServletResponse.SC_FORBIDDEN, "withdrawn super admin status");
        require(exchange.chainCalls == 0, "withdrawn super admin chain");
        require(exchange.invalidated, "withdrawn super admin session invalidation");
        require(exchange.attributes.isEmpty(), "withdrawn super admin session cleanup");
    }

    private static void verifyDemotionClearsAdminState() throws Exception {
        CountingUserDAO dao = new CountingUserDAO(admin("MEMBER", "ACTIVE"), false);
        Exchange exchange = exchange(admin("ADMIN", "ACTIVE"), true);
        new AdminFilter(dao).doFilter(exchange.request, exchange.response, exchange.chain);
        require(dao.calls == 1, "demotion lookup count");
        require(exchange.status == HttpServletResponse.SC_FORBIDDEN, "demotion status");
        require(!exchange.attributes.containsKey(SessionKey.ADMIN_USER), "demotion admin user cleanup");
        require(!exchange.attributes.containsKey(SessionKey.ADMIN_ACCESS_VERIFIED), "demotion verification cleanup");
        require(!exchange.attributes.containsKey(SessionKey.ADMIN_ACCESS_VERIFIED_AT), "demotion timestamp cleanup");
        require(exchange.attributes.containsKey(SessionKey.LOGIN_USER), "demotion login session retention");
        require("MEMBER".equals(((UserDTO) exchange.attributes.get(SessionKey.LOGIN_USER)).getRoleCode()),
                "demotion login role refresh");
        require(!exchange.invalidated, "demotion session remains valid");
    }

    private static void verifyActiveAdminUsesOneLookup() throws Exception {
        CountingUserDAO dao = new CountingUserDAO(admin("ADMIN", "ACTIVE"), false);
        Exchange exchange = exchange(admin("ADMIN", "ACTIVE"), true);
        new AdminFilter(dao).doFilter(exchange.request, exchange.response, exchange.chain);
        require(dao.calls == 1, "active admin lookup count");
        require(exchange.chainCalls == 1, "active admin chain");
        require(exchange.status == 0, "active admin status");
    }

    private static UserDTO admin(String role, String status) {
        UserDTO user = new UserDTO();
        user.setUserId(42L);
        user.setLoginId("verification-admin");
        user.setRoleCode(role);
        user.setStatus(status);
        return user;
    }

    private static Exchange exchange(UserDTO loginUser, boolean verified) {
        Exchange exchange = new Exchange();
        exchange.attributes.put(SessionKey.LOGIN_USER, loginUser);
        exchange.attributes.put(SessionKey.ADMIN_USER, loginUser);
        if (verified) {
            exchange.attributes.put(SessionKey.ADMIN_ACCESS_VERIFIED, true);
            exchange.attributes.put(SessionKey.ADMIN_ACCESS_VERIFIED_AT, System.currentTimeMillis());
        }
        exchange.session = proxy(HttpSession.class, (method, args) -> switch (method.getName()) {
            case "getAttribute" -> exchange.attributes.get(args[0]);
            case "setAttribute" -> exchange.attributes.put((String) args[0], args[1]);
            case "removeAttribute" -> exchange.attributes.remove(args[0]);
            case "invalidate" -> {
                exchange.attributes.clear();
                exchange.invalidated = true;
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        exchange.request = proxy(HttpServletRequest.class, (method, args) -> switch (method.getName()) {
            case "getSession" -> exchange.session;
            case "getServletPath" -> "/admin/dashboard";
            case "getContextPath" -> "/scuredweb";
            case "getQueryString" -> null;
            default -> defaultValue(method.getReturnType());
        });
        exchange.response = proxy(HttpServletResponse.class, (method, args) -> {
            if ("sendError".equals(method.getName())) exchange.status = (Integer) args[0];
            if ("sendRedirect".equals(method.getName())) exchange.redirect = (String) args[0];
            return defaultValue(method.getReturnType());
        });
        exchange.chain = (request, response) -> exchange.chainCalls++;
        return exchange;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static void require(boolean condition, String scenario) {
        if (!condition) throw new AssertionError(scenario);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    private static final class Exchange {
        private final Map<String, Object> attributes = new HashMap<>();
        private HttpSession session;
        private HttpServletRequest request;
        private HttpServletResponse response;
        private FilterChain chain;
        private int status;
        private int chainCalls;
        private String redirect;
        private boolean invalidated;
    }

    private static final class CountingUserDAO extends UserDAO {
        private final UserDTO result;
        private final boolean fail;
        private int calls;

        private CountingUserDAO(UserDTO result, boolean fail) {
            this.result = result;
            this.fail = fail;
        }

        @Override
        public UserDTO findById(Long userId) throws SQLException {
            calls++;
            if (fail) throw new SQLException("verification database outage");
            return result;
        }
    }
}
