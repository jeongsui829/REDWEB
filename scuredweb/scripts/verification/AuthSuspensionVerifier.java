import com.scuredweb.auth.dto.LoginRequestDTO;
import com.scuredweb.auth.service.AuthService;
import com.scuredweb.common.security.PasswordUtil;
import com.scuredweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuthSuspensionVerifier {
    private record TestAccount(long userId, String loginId, String status, LocalDateTime suspendedUntil,
                               boolean loginExpected) {
    }

    private AuthSuspensionVerifier() {
    }

    public static void main(String[] args) throws Exception {
        String password = UUID.randomUUID().toString() + "!9a";
        String passwordHash = PasswordUtil.hash(password);
        String suffix = Long.toString(System.currentTimeMillis(), 36);
        List<TestAccount> accounts = new ArrayList<>();

        try (Connection connection = VerificationDatabase.connect()) {
            accounts.add(insert(connection, suffix, "expired", "SUSPENDED", LocalDateTime.now().minusMinutes(5), true, passwordHash));
            accounts.add(insert(connection, suffix, "future", "SUSPENDED", LocalDateTime.now().plusMinutes(30), false, passwordHash));
            accounts.add(insert(connection, suffix, "indefinite", "SUSPENDED", null, false, passwordHash));
            accounts.add(insert(connection, suffix, "active", "ACTIVE", null, true, passwordHash));
            accounts.add(insert(connection, suffix, "withdrawn", "WITHDRAWN", null, false, passwordHash));

            try {
                AuthService authService = new AuthService();
                String genericMessage = null;
                for (TestAccount account : accounts) {
                    LoginRequestDTO request = new LoginRequestDTO();
                    request.setLoginId(account.loginId());
                    request.setPassword(password);
                    UserDTO user = authService.login(request);
                    boolean loggedIn = user != null;
                    State state = readState(connection, account.userId());
                    int authLogCount = authLogCount(connection, account.userId());
                    System.out.printf("%s|LOGIN=%s|STATUS=%s|SUSPENDED_UNTIL_NULL=%s|LAST_LOGIN=%s|AUTH_LOG=%d%n",
                            account.loginId(), loggedIn, state.status(), state.suspendedUntil() == null,
                            state.lastLoginAt() != null, authLogCount);
                    if (loggedIn != account.loginExpected()) {
                        throw new AssertionError("Unexpected login result for " + account.loginId());
                    }
                    if (loggedIn && (state.lastLoginAt() == null || authLogCount != 1)) {
                        throw new AssertionError("Successful login history was not recorded atomically");
                    }
                    if (account.loginId().contains("expired")
                            && (!"ACTIVE".equals(state.status()) || state.suspendedUntil() != null)) {
                        throw new AssertionError("Expired suspension was not atomically released");
                    }
                    if (account.loginId().contains("future")
                            && (!"SUSPENDED".equals(state.status()) || state.suspendedUntil() == null)) {
                        throw new AssertionError("Future suspension changed unexpectedly");
                    }
                    if (account.loginId().contains("indefinite")
                            && (!"SUSPENDED".equals(state.status()) || state.suspendedUntil() != null)) {
                        throw new AssertionError("Indefinite suspension changed unexpectedly");
                    }

                    String message = authService.resolveLoginFailureMessage(account.loginId());
                    if (genericMessage == null) {
                        genericMessage = message;
                    } else if (!genericMessage.equals(message)) {
                        throw new AssertionError("Login failure message exposed account state");
                    }
                }
                System.out.println("AUTH_SUSPENSION_VERIFICATION=PASS");
            } finally {
                cleanup(connection, accounts);
            }
        }
    }

    private static TestAccount insert(Connection connection, String suffix, String label, String status,
                                      LocalDateTime suspendedUntil, boolean loginExpected, String passwordHash)
            throws Exception {
        long userId;
        try (PreparedStatement statement = connection.prepareStatement("SELECT SEQ_RW_USER.NEXTVAL FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            userId = resultSet.getLong(1);
        }
        String loginId = "verify_suspend_" + label + "_" + suffix;
        String sql = """
                INSERT INTO RW_USER (
                    USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL,
                    STATUS, ROLE_CODE, EMAIL_VERIFIED_YN, SUSPENDED_UNTIL, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'MEMBER', 'N', ?, SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, loginId);
            statement.setString(3, passwordHash);
            statement.setString(4, "Suspension Verification");
            statement.setString(5, loginId);
            statement.setString(6, loginId + "@example.invalid");
            statement.setString(7, status);
            statement.setTimestamp(8, suspendedUntil == null ? null : Timestamp.valueOf(suspendedUntil));
            statement.executeUpdate();
        }
        return new TestAccount(userId, loginId, status, suspendedUntil, loginExpected);
    }

    private static State readState(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT STATUS, SUSPENDED_UNTIL, LAST_LOGIN_AT FROM RW_USER WHERE USER_ID = ?")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Verification user disappeared: " + userId);
                }
                Timestamp timestamp = resultSet.getTimestamp(2);
                Timestamp lastLoginAt = resultSet.getTimestamp(3);
                return new State(resultSet.getString(1), timestamp == null ? null : timestamp.toLocalDateTime(),
                        lastLoginAt == null ? null : lastLoginAt.toLocalDateTime());
            }
        }
    }

    private static int authLogCount(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM RW_USER_AUTH_LOG WHERE USER_ID = ?")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static void cleanup(Connection connection, List<TestAccount> accounts) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM RW_USER_AUTH_LOG WHERE USER_ID = ?")) {
            for (TestAccount account : accounts) {
                statement.setLong(1, account.userId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM RW_USER WHERE USER_ID = ?")) {
            for (TestAccount account : accounts) {
                statement.setLong(1, account.userId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private record State(String status, LocalDateTime suspendedUntil, LocalDateTime lastLoginAt) {
    }
}
