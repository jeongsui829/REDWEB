import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

public final class ReportResolutionVerifier {
    private static final String FAILURE_TRIGGER = "TRG_VERIFY_REPORT_SUSPEND_FAIL";

    private ReportResolutionVerifier() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection connection = VerificationDatabase.connect()) {
            long adminId = insertUser(connection, "admin", "ADMIN");
            long reporterId = insertUser(connection, "reporter", "MEMBER");
            long normalTargetId = insertUser(connection, "normal_target", "MEMBER");
            long failingTargetId = insertUser(connection, "failing_target", "MEMBER");
            long normalReportId = insertReport(connection, reporterId, normalTargetId);
            long failingReportId = insertReport(connection, reporterId, failingTargetId);
            UserDTO admin = user(adminId, "ADMIN");
            try {
                LocalDateTime started = LocalDateTime.now();
                if (!SocialService.resolveReport(normalReportId, admin)) {
                    throw new AssertionError("Normal report resolution failed");
                }
                ResolvedState normal = readState(connection, normalReportId, normalTargetId);
                long minutes = Duration.between(started, normal.suspendedUntil()).toMinutes();
                if (!"RESOLVED".equals(normal.reportStatus()) || !"SUSPENDED".equals(normal.userStatus())
                        || normal.penaltySummary() == null || !normal.penaltySummary().contains("30분")
                        || minutes < 29 || minutes > 30) {
                    throw new AssertionError("Penalty summary and suspension expiry are inconsistent");
                }

                installFailureTrigger(connection, failingTargetId);
                if (SocialService.resolveReport(failingReportId, admin)) {
                    throw new AssertionError("Report transaction failure was reported as success");
                }
                ResolvedState failed = readState(connection, failingReportId, failingTargetId);
                if (!"PENDING".equals(failed.reportStatus()) || !"ACTIVE".equals(failed.userStatus())) {
                    throw new AssertionError("Report/user transaction did not roll back atomically");
                }
                System.out.printf("REPORT_RESOLUTION_VERIFICATION=PASS|REPORT_ID=%d|PENALTY=%s|MINUTES=%d%n",
                        normalReportId, normal.penaltySummary(), minutes);
            } finally {
                dropFailureTrigger(connection);
                deleteReport(connection, normalReportId);
                deleteReport(connection, failingReportId);
                new UserDAO().deleteUserHard(normalTargetId);
                new UserDAO().deleteUserHard(failingTargetId);
                new UserDAO().deleteUserHard(reporterId);
                new UserDAO().deleteUserHard(adminId);
            }
        }
    }

    private static long insertUser(Connection connection, String label, String role) throws Exception {
        long userId;
        try (PreparedStatement statement = connection.prepareStatement("SELECT SEQ_RW_USER.NEXTVAL FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            userId = resultSet.getLong(1);
        }
        String loginId = "verify_report_" + label + "_" + userId;
        String sql = """
                INSERT INTO RW_USER (
                    USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL,
                    STATUS, ROLE_CODE, EMAIL_VERIFIED_YN, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, 'verification-only', 'Report Verification', ?, ?,
                          'ACTIVE', ?, 'N', SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, loginId);
            statement.setString(3, loginId);
            statement.setString(4, loginId + "@example.invalid");
            statement.setString(5, role);
            statement.executeUpdate();
        }
        return userId;
    }

    private static long insertReport(Connection connection, long reporterId, long targetId) throws Exception {
        long reportId;
        try (PreparedStatement statement = connection.prepareStatement("SELECT SEQ_RW_REPORT.NEXTVAL FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            reportId = resultSet.getLong(1);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO RW_REPORT (
                    REPORT_ID, REPORTER_ID, TARGET_TYPE, TARGET_ID, REASON_CODE,
                    REASON_DETAIL, STATUS, CREATED_AT
                ) VALUES (?, ?, 'USER', ?, 'VERIFY', 'verification', 'PENDING', SYSTIMESTAMP)
                """)) {
            statement.setLong(1, reportId);
            statement.setLong(2, reporterId);
            statement.setLong(3, targetId);
            statement.executeUpdate();
        }
        return reportId;
    }

    private static UserDTO user(long userId, String role) {
        UserDTO user = new UserDTO();
        user.setUserId(userId);
        user.setRoleCode(role);
        return user;
    }

    private static ResolvedState readState(Connection connection, long reportId, long userId) throws Exception {
        String sql = """
                SELECT R.STATUS, R.PENALTY_SUMMARY, U.STATUS, U.SUSPENDED_UNTIL
                  FROM RW_REPORT R
                  JOIN RW_USER U ON U.USER_ID = ?
                 WHERE R.REPORT_ID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                Timestamp until = resultSet.getTimestamp(4);
                return new ResolvedState(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                        until == null ? null : until.toLocalDateTime());
            }
        }
    }

    private static void installFailureTrigger(Connection connection, long userId) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE OR REPLACE TRIGGER " + FAILURE_TRIGGER
                    + " BEFORE UPDATE OF STATUS ON RW_USER FOR EACH ROW "
                    + "WHEN (NEW.USER_ID = " + userId + ") "
                    + "BEGIN RAISE_APPLICATION_ERROR(-20996, 'verification report failure'); END;");
        }
    }

    private static void dropFailureTrigger(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER " + FAILURE_TRIGGER);
        } catch (Exception ignored) {
        }
    }

    private static void deleteReport(Connection connection, long reportId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM RW_REPORT WHERE REPORT_ID = ?")) {
            statement.setLong(1, reportId);
            statement.executeUpdate();
        }
    }

    private record ResolvedState(String reportStatus, String penaltySummary, String userStatus,
                                 LocalDateTime suspendedUntil) {
    }
}
