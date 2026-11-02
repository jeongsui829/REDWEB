import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public final class SocialFailureVerifier {
    private static final String BIO_TRIGGER = "TRG_VERIFY_BIO_FAILURE";
    private static final String MESSAGE_TRIGGER = "TRG_VERIFY_MESSAGE_FAILURE";
    private static final String NOTIFICATION_TRIGGER = "TRG_VERIFY_NOTI_FAILURE";

    private SocialFailureVerifier() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection connection = VerificationDatabase.connect()) {
            long firstId = insertUser(connection, "first");
            long secondId = insertUser(connection, "second");
            UserDTO first = user(firstId, "verify_social_first_" + firstId, "before");
            UserDTO second = user(secondId, "verify_social_second_" + secondId, "before");
            try {
                verifyNormalOperations(connection, first);
                insertMessage(connection, secondId, firstId, "N");
                insertMessage(connection, secondId, firstId, "Y");
                insertNotification(connection, firstId, "N");
                insertNotification(connection, firstId, "Y");
                installFailureTriggers(connection, firstId);
                verifyFailures(first, second);
                System.out.printf("SOCIAL_FAILURE_VERIFICATION=PASS|USER_ID=%d%n", firstId);
            } finally {
                dropFailureTriggers(connection);
                new UserDAO().deleteUserHard(firstId);
                new UserDAO().deleteUserHard(secondId);
            }
        }
    }

    private static void verifyNormalOperations(Connection connection, UserDTO user) throws Exception {
        if (!SocialService.updateBio(user, "normal bio") || !"normal bio".equals(user.getBio())) {
            throw new AssertionError("Normal bio update failed");
        }
        insertMessage(connection, user.getUserId(), user.getUserId(), "N");
        if (!SocialService.markMessagesRead(user.getUserId())) {
            throw new AssertionError("Normal message read update failed");
        }
        SocialService.DeleteResult messageDelete = SocialService.deleteReadMessages(user.getUserId());
        if (!messageDelete.success() || messageDelete.deletedCount() < 1) {
            throw new AssertionError("Normal read message delete failed");
        }
        insertNotification(connection, user.getUserId(), "N");
        if (!SocialService.markNotificationsRead(user.getUserId())) {
            throw new AssertionError("Normal notification read update failed");
        }
        SocialService.DeleteResult notificationDelete = SocialService.deleteReadNotifications(user.getUserId());
        if (!notificationDelete.success() || notificationDelete.deletedCount() < 1) {
            throw new AssertionError("Normal read notification delete failed");
        }
    }

    private static void verifyFailures(UserDTO first, UserDTO second) {
        String originalBio = first.getBio();
        if (SocialService.updateBio(first, "must not reach local state")) {
            throw new AssertionError("Bio DB failure was reported as success");
        }
        if (!originalBio.equals(first.getBio())) {
            throw new AssertionError("Bio DB failure changed the local user");
        }
        if (SocialService.markMessagesRead(first.getUserId())) {
            throw new AssertionError("Message read DB failure was hidden");
        }
        if (SocialService.deleteReadMessages(first.getUserId()).success()) {
            throw new AssertionError("Read message delete DB failure was reported as zero rows");
        }
        if (SocialService.markNotificationsRead(first.getUserId())) {
            throw new AssertionError("Notification read DB failure was hidden");
        }
        if (SocialService.deleteReadNotifications(first.getUserId()).success()) {
            throw new AssertionError("Read notification delete DB failure was reported as zero rows");
        }
        if (SocialService.pushNotification(first.getUserId(), "verification", "verification", "/")) {
            throw new AssertionError("Notification insert DB failure was hidden");
        }
        if (!SocialService.sendMessage(second, first, "core succeeds", "notification fails")) {
            throw new AssertionError("Notification failure changed core message success");
        }
    }

    private static long insertUser(Connection connection, String label) throws Exception {
        long userId;
        try (PreparedStatement statement = connection.prepareStatement("SELECT SEQ_RW_USER.NEXTVAL FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            userId = resultSet.getLong(1);
        }
        String loginId = "verify_social_" + label + "_" + userId;
        String sql = """
                INSERT INTO RW_USER (
                    USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL,
                    BIO, STATUS, ROLE_CODE, EMAIL_VERIFIED_YN, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, 'verification-only', 'Social Verification', ?, ?, 'before',
                          'ACTIVE', 'MEMBER', 'N', SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, loginId);
            statement.setString(3, loginId);
            statement.setString(4, loginId + "@example.invalid");
            statement.executeUpdate();
        }
        return userId;
    }

    private static UserDTO user(long userId, String nickname, String bio) {
        UserDTO user = new UserDTO();
        user.setUserId(userId);
        user.setLoginId(nickname);
        user.setNickname(nickname);
        user.setBio(bio);
        return user;
    }

    private static void insertMessage(Connection connection, long senderId, long receiverId, String read) throws Exception {
        String sql = """
                INSERT INTO RW_MESSAGE (MESSAGE_ID, SENDER_USER_ID, RECEIVER_USER_ID, TITLE, CONTENT, READ_YN, CREATED_AT)
                VALUES (SEQ_RW_MESSAGE.NEXTVAL, ?, ?, 'verification', 'verification', ?, SYSTIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, senderId);
            statement.setLong(2, receiverId);
            statement.setString(3, read);
            statement.executeUpdate();
        }
    }

    private static void insertNotification(Connection connection, long userId, String read) throws Exception {
        String sql = """
                INSERT INTO RW_NOTIFICATION (NOTI_ID, USER_ID, NOTI_TYPE, TITLE, MESSAGE, TARGET_URL, READ_YN, CREATED_AT)
                VALUES (SEQ_RW_NOTIFICATION.NEXTVAL, ?, 'VERIFY', 'verification', 'verification', '/', ?, SYSTIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, read);
            statement.executeUpdate();
        }
    }

    private static void installFailureTriggers(Connection connection, long userId) throws Exception {
        executeDdl(connection, "CREATE OR REPLACE TRIGGER " + BIO_TRIGGER
                + " BEFORE UPDATE OF BIO ON RW_USER FOR EACH ROW "
                + "WHEN (NEW.USER_ID = " + userId + ") "
                + "BEGIN RAISE_APPLICATION_ERROR(-20991, 'verification bio failure'); END;");
        executeDdl(connection, "CREATE OR REPLACE TRIGGER " + MESSAGE_TRIGGER
                + " BEFORE UPDATE OR DELETE ON RW_MESSAGE FOR EACH ROW "
                + "BEGIN IF :OLD.RECEIVER_USER_ID = " + userId
                + " THEN RAISE_APPLICATION_ERROR(-20992, 'verification message failure'); END IF; END;");
        executeDdl(connection, "CREATE OR REPLACE TRIGGER " + NOTIFICATION_TRIGGER
                + " BEFORE INSERT OR UPDATE OR DELETE ON RW_NOTIFICATION FOR EACH ROW "
                + "BEGIN IF (INSERTING AND :NEW.USER_ID = " + userId + ") OR "
                + "((UPDATING OR DELETING) AND :OLD.USER_ID = " + userId + ") "
                + "THEN RAISE_APPLICATION_ERROR(-20993, 'verification notification failure'); END IF; END;");
    }

    private static void dropFailureTriggers(Connection connection) {
        for (String trigger : new String[]{BIO_TRIGGER, MESSAGE_TRIGGER, NOTIFICATION_TRIGGER}) {
            try {
                executeDdl(connection, "DROP TRIGGER " + trigger);
            } catch (Exception ignored) {
            }
        }
    }

    private static void executeDdl(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
