import com.scuredweb.user.dao.UserDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public final class AttachmentFaultHarness {
    private static final String ATTACHMENT_TRIGGER = "TRG_VERIFY_ATTACHMENT_FAILURE";
    private static final String DELETE_TRIGGER = "TRG_VERIFY_POST_DELETE_FAILURE";

    private AttachmentFaultHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("mode is required");
        }
        try (Connection connection = VerificationDatabase.connect()) {
            switch (args[0]) {
                case "install-save-failure" -> install(connection, false);
                case "install-save-and-delete-failure" -> install(connection, true);
                case "clear" -> clear(connection);
                case "find-title" -> findTitle(connection, requiredArg(args));
                case "find-user" -> findUser(connection, requiredArg(args));
                case "list-verification-users" -> listVerificationUsers(connection);
                case "cleanup-title" -> cleanupTitle(connection, requiredArg(args));
                case "cleanup-user" -> cleanupUser(connection, requiredArg(args));
                case "promote-admin" -> promoteAdmin(connection, requiredArg(args));
                case "sequences" -> printSequences(connection);
                default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
            }
        }
    }

    private static String requiredArg(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            throw new IllegalArgumentException("mode argument is required");
        }
        return args[1];
    }

    private static void install(Connection connection, boolean failDelete) throws Exception {
        clear(connection);
        execute(connection, """
                CREATE OR REPLACE TRIGGER TRG_VERIFY_ATTACHMENT_FAILURE
                BEFORE INSERT ON RW_ATTACHMENT
                FOR EACH ROW
                DECLARE
                    v_title RW_POST.TITLE%TYPE;
                BEGIN
                    SELECT TITLE INTO v_title FROM RW_POST WHERE POST_ID = :NEW.POST_ID;
                    IF v_title LIKE 'VERIFY_ATTACHMENT_%' THEN
                        RAISE_APPLICATION_ERROR(-20994, 'verification attachment failure');
                    END IF;
                END;
                """);
        if (failDelete) {
            execute(connection, """
                    CREATE OR REPLACE TRIGGER TRG_VERIFY_POST_DELETE_FAILURE
                    BEFORE DELETE ON RW_POST
                    FOR EACH ROW
                    BEGIN
                        IF :OLD.TITLE LIKE 'VERIFY_ATTACHMENT_PARTIAL_%' THEN
                            RAISE_APPLICATION_ERROR(-20995, 'verification compensation failure');
                        END IF;
                    END;
                    """);
        }
        System.out.println(failDelete ? "FAULT_MODE=SAVE_AND_DELETE" : "FAULT_MODE=SAVE_ONLY");
    }

    private static void clear(Connection connection) {
        drop(connection, ATTACHMENT_TRIGGER);
        drop(connection, DELETE_TRIGGER);
    }

    private static void findTitle(Connection connection, String title) throws Exception {
        String sql = """
                SELECT P.POST_ID,
                       (SELECT COUNT(*) FROM RW_ATTACHMENT A WHERE A.POST_ID = P.POST_ID)
                  FROM RW_POST P
                 WHERE P.TITLE = ?
                """;
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    count++;
                    System.out.printf("POST_ID=%d|ATTACHMENT_COUNT=%d%n",
                            resultSet.getLong(1), resultSet.getInt(2));
                }
            }
        }
        System.out.println("POST_COUNT=" + count);
    }

    private static void findUser(Connection connection, String loginId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT USER_ID, CASE WHEN PHONE IS NULL THEN 1 ELSE 0 END FROM RW_USER WHERE LOGIN_ID = ?")) {
            statement.setString(1, loginId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    System.out.println("USER_COUNT=0");
                    return;
                }
                System.out.printf("USER_COUNT=1|USER_ID=%d|PHONE_IS_NULL=%d%n",
                        resultSet.getLong(1), resultSet.getInt(2));
            }
        }
    }

    private static void listVerificationUsers(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT USER_ID, LOGIN_ID
                  FROM RW_USER
                 WHERE LOGIN_ID LIKE 'verify\\_%' ESCAPE '\\'
                 ORDER BY USER_ID
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.printf("USER_ID=%d|LOGIN_ID=%s%n", resultSet.getLong(1), resultSet.getString(2));
            }
        }
    }

    private static void cleanupTitle(Connection connection, String title) throws Exception {
        Long postId = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT POST_ID FROM RW_POST WHERE TITLE = ?")) {
            statement.setString(1, title);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    postId = resultSet.getLong(1);
                }
            }
        }
        if (postId == null) {
            System.out.println("CLEANUP_POST_ID=NONE");
            return;
        }
        connection.setAutoCommit(false);
        try {
            delete(connection, "DELETE FROM RW_REPORT WHERE TARGET_TYPE = 'POST' AND TARGET_ID = ?", postId);
            delete(connection, "DELETE FROM RW_RECENT_VIEW WHERE POST_ID = ?", postId);
            delete(connection, "DELETE FROM RW_POST_REACTION WHERE POST_ID = ?", postId);
            delete(connection, "DELETE FROM RW_ATTACHMENT WHERE POST_ID = ?", postId);
            delete(connection, "DELETE FROM RW_POST_TAG WHERE POST_ID = ?", postId);
            delete(connection, "DELETE FROM RW_COMMENT WHERE POST_ID = ?", postId);
            delete(connection, "DELETE FROM RW_POST WHERE POST_ID = ?", postId);
            connection.commit();
            System.out.println("CLEANUP_POST_ID=" + postId);
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void cleanupUser(Connection connection, String loginId) throws Exception {
        Long userId = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT USER_ID FROM RW_USER WHERE LOGIN_ID = ?")) {
            statement.setString(1, loginId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    userId = resultSet.getLong(1);
                }
            }
        }
        if (userId == null) {
            System.out.println("CLEANUP_USER_ID=NONE");
            return;
        }
        if (!new UserDAO().deleteUserHard(userId)) {
            throw new AssertionError("Verification user hard delete failed: " + userId);
        }
        System.out.println("CLEANUP_USER_ID=" + userId);
    }

    private static void promoteAdmin(Connection connection, String loginId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE RW_USER SET ROLE_CODE = 'ADMIN', UPDATED_AT = SYSTIMESTAMP WHERE LOGIN_ID = ?")) {
            statement.setString(1, loginId);
            if (statement.executeUpdate() != 1) {
                throw new AssertionError("Verification admin promotion failed");
            }
        }
        System.out.println("PROMOTED_ADMIN=1");
    }

    private static void printSequences(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT SEQUENCE_NAME, LAST_NUMBER
                  FROM USER_SEQUENCES
                 WHERE SEQUENCE_NAME IN ('SEQ_RW_USER', 'SEQ_RW_POST', 'SEQ_RW_ATTACHMENT')
                 ORDER BY SEQUENCE_NAME
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.printf("%s=%d%n", resultSet.getString(1), resultSet.getLong(2));
            }
        }
    }

    private static void delete(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void drop(Connection connection, String triggerName) {
        try {
            execute(connection, "DROP TRIGGER " + triggerName);
        } catch (Exception ignored) {
        }
    }
}
