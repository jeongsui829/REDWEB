import com.scuredweb.user.dao.UserDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class HardDeleteVerifier {
    private HardDeleteVerifier() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection connection = VerificationDatabase.connect()) {
            long userId = nextUserId(connection);
            long postId = findPostForReaction(connection);
            insertUser(connection, userId);
            try {
                insertReaction(connection, postId, userId);
                if (!new UserDAO().deleteUserHard(userId)) {
                    throw new AssertionError("Hard delete returned false");
                }
                assertCount(connection, "SELECT COUNT(*) FROM RW_USER WHERE USER_ID = ?", userId, 0);
                assertCount(connection, "SELECT COUNT(*) FROM RW_POST_REACTION WHERE USER_ID = ?", userId, 0);
                System.out.printf("HARD_DELETE_VERIFICATION=PASS|USER_ID=%d|OTHER_POST_ID=%d%n", userId, postId);
            } finally {
                cleanup(connection, userId);
            }
        }
    }

    private static long nextUserId(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT SEQ_RW_USER.NEXTVAL FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long findPostForReaction(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT MIN(POST_ID) FROM RW_POST");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            long postId = resultSet.getLong(1);
            if (resultSet.wasNull()) {
                throw new IllegalStateException("RW_POST needs one existing row for the cross-user reaction test");
            }
            return postId;
        }
    }

    private static void insertUser(Connection connection, long userId) throws Exception {
        String value = "verify_hard_delete_" + userId;
        String sql = """
                INSERT INTO RW_USER (
                    USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL,
                    STATUS, ROLE_CODE, EMAIL_VERIFIED_YN, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, 'verification-only', 'Hard Delete Verification', ?, ?,
                          'ACTIVE', 'MEMBER', 'N', SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, value);
            statement.setString(3, value);
            statement.setString(4, value + "@example.invalid");
            statement.executeUpdate();
        }
    }

    private static void insertReaction(Connection connection, long postId, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO RW_POST_REACTION (POST_ID, USER_ID, REACTION_TYPE, CREATED_AT) VALUES (?, ?, 'LIKE', SYSTIMESTAMP)")) {
            statement.setLong(1, postId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    private static void assertCount(Connection connection, String sql, long id, int expected) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != expected) {
                    throw new AssertionError("Unexpected row count for exact ID " + id);
                }
            }
        }
    }

    private static void cleanup(Connection connection, long userId) throws Exception {
        try (PreparedStatement reaction = connection.prepareStatement("DELETE FROM RW_POST_REACTION WHERE USER_ID = ?");
             PreparedStatement user = connection.prepareStatement("DELETE FROM RW_USER WHERE USER_ID = ?")) {
            reaction.setLong(1, userId);
            reaction.executeUpdate();
            user.setLong(1, userId);
            user.executeUpdate();
        }
    }
}
