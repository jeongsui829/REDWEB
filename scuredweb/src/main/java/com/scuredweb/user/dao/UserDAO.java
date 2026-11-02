package com.scuredweb.user.dao;

import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.comment.dto.CommentDTO;
import com.scuredweb.common.util.DBUtil;
import com.scuredweb.notification.dto.NotificationDTO;
import com.scuredweb.user.dto.LoginHistoryDTO;
import com.scuredweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserDAO {
    public List<String> findAttachmentSavedNamesForUserDeletion(Long userId) throws SQLException {
        String sql = """
                SELECT DISTINCT SAVED_NAME
                  FROM RW_ATTACHMENT
                 WHERE UPLOADER_ID = ?
                    OR POST_ID IN (SELECT POST_ID FROM RW_POST WHERE USER_ID = ?)
                """;
        List<String> savedNames = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    savedNames.add(resultSet.getString("SAVED_NAME"));
                }
            }
        }
        return savedNames;
    }

    public UserDTO findById(Long userId) throws SQLException {
        String sql = """
                SELECT USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL, PHONE, PROFILE_IMAGE_PATH, BIO,
                       ROLE_CODE, STATUS, AUTH_VERSION, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
                  FROM RW_USER
                 WHERE USER_ID = ?
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                UserDTO user = new UserDTO();
                user.setUserId(resultSet.getLong("USER_ID"));
                user.setLoginId(resultSet.getString("LOGIN_ID"));
                user.setPasswordHash(resultSet.getString("PASSWORD_HASH"));
                user.setUserName(resultSet.getString("USER_NAME"));
                user.setNickname(resultSet.getString("NICKNAME"));
                user.setEmail(resultSet.getString("EMAIL"));
                user.setPhone(resultSet.getString("PHONE"));
                user.setProfileImagePath(resultSet.getString("PROFILE_IMAGE_PATH"));
                user.setBio(resultSet.getString("BIO"));
                user.setRoleCode(resultSet.getString("ROLE_CODE"));
                user.setStatus(resultSet.getString("STATUS"));
        user.setAuthVersion(resultSet.getLong("AUTH_VERSION"));
                user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
                Timestamp lastLoginAt = resultSet.getTimestamp("LAST_LOGIN_AT");
                Timestamp createdAt = resultSet.getTimestamp("CREATED_AT");
                user.setLastLoginAt(lastLoginAt == null ? null : lastLoginAt.toLocalDateTime());
                user.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                return user;
            }
        }
    }

    public List<LoginHistoryDTO> findLoginHistory(Long userId) throws SQLException {
        String sql = """
                SELECT AUTH_LOG_ID, LOGIN_IP, USER_AGENT, LOGIN_RESULT, LOGIN_AT
                  FROM RW_USER_AUTH_LOG
                 WHERE USER_ID = ?
                 ORDER BY LOGIN_AT DESC
                 FETCH FIRST 5 ROWS ONLY
                """;
        List<LoginHistoryDTO> histories = new ArrayList<>();

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    LoginHistoryDTO history = new LoginHistoryDTO();
                    history.setAuthLogId(resultSet.getLong("AUTH_LOG_ID"));
                    history.setLoginIp(resultSet.getString("LOGIN_IP"));
                    history.setUserAgent(resultSet.getString("USER_AGENT"));
                    history.setLoginResult(resultSet.getString("LOGIN_RESULT"));
                    histories.add(history);
                }
            }
        }
        return histories;
    }

    public List<NotificationDTO> findNotifications(Long userId) throws SQLException {
        String sql = """
                SELECT NOTI_ID, TITLE, MESSAGE, TARGET_URL, READ_YN, CREATED_AT
                  FROM RW_NOTIFICATION
                 WHERE USER_ID = ?
                 ORDER BY CREATED_AT DESC
                 FETCH FIRST 5 ROWS ONLY
                """;
        List<NotificationDTO> notifications = new ArrayList<>();

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    NotificationDTO notification = new NotificationDTO();
                    notification.setNotificationId(resultSet.getLong("NOTI_ID"));
                    notification.setTitle(resultSet.getString("TITLE"));
                    notification.setMessage(resultSet.getString("MESSAGE"));
                    notification.setTargetUrl(resultSet.getString("TARGET_URL"));
                    notification.setRead("Y".equals(resultSet.getString("READ_YN")));
                    notifications.add(notification);
                }
            }
        }
        return notifications;
    }

    /** Offline compatibility only. Production requests must supply current password. */
    @Deprecated
    public boolean withdrawUser(Long userId) throws SQLException {
        if (com.scuredweb.common.config.AppConfig.production()) return false;
        return removeUserData(userId, true, null);
    }

    public boolean withdrawUser(Long userId, String currentPassword) throws SQLException {
        if (currentPassword == null || currentPassword.isBlank()) return false;
        return removeUserData(userId, true, currentPassword);
    }

    /** Explicit offline fixture cleanup only. Public withdrawal must use withdrawUser. */
    public boolean deleteUserHard(Long userId) throws SQLException {
        return removeUserData(userId, false, null);
    }

    private boolean removeUserData(Long userId, boolean preserveReplies, String currentPassword) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (preserveReplies) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT PASSWORD_HASH FROM RW_USER WHERE USER_ID = ? AND STATUS = 'ACTIVE' AND ROLE_CODE = 'MEMBER' FOR UPDATE")) {
                        statement.setLong(1, userId);
                        try (ResultSet rows = statement.executeQuery()) {
                            if (!rows.next() || (currentPassword != null && !com.scuredweb.common.security.PasswordUtil.verifyPassword(currentPassword, rows.getString(1)))) { connection.rollback(); return false; }
                        }
                    }
                }
                validateUserReferences(connection);
                if (hasColumn(connection, "RW_PASSWORD_RESET_TOKEN", "USER_ID")) {
                    executeUpdate(connection, "DELETE FROM RW_PASSWORD_RESET_TOKEN WHERE USER_ID = ?", userId);
                }
                executeUpdate(connection, "DELETE FROM RW_NOTIFICATION WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_MESSAGE WHERE SENDER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_MESSAGE WHERE RECEIVER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_GUESTBOOK WHERE WRITER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_GUESTBOOK WHERE OWNER_USER_ID = ?", userId);
                if (!preserveReplies) executeUpdate(connection, "DELETE FROM RW_USER_AUTH_LOG WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_RECENT_VIEW WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_ADMIN_USER_ROLE WHERE USER_ID = ?", userId);
                if (!preserveReplies) {
                executeUpdate(connection, "DELETE FROM RW_ADMIN_ACTION_LOG WHERE ADMIN_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_REPORT WHERE REPORTER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_REPORT WHERE TARGET_ID = ? AND TARGET_TYPE = 'USER'", userId);
                }
                executeUpdate(connection, "DELETE FROM RW_NOTICE WHERE CREATED_BY = ?", userId);
                executeUpdate(connection, "UPDATE RW_ACCESS_LOG SET USER_ID = NULL WHERE USER_ID = ?", userId);
                executeUpdate(connection, "UPDATE RW_ERROR_LOG SET USER_ID = NULL WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_POST_REACTION WHERE USER_ID = ?", userId);

                deletePostDependencies(connection, userId, preserveReplies);
                if (preserveReplies) new com.scuredweb.comment.dao.CommentDAO().softDeleteCommentsByUser(connection, userId);
                else deleteCommentTreeByUser(connection, userId);
                executeUpdate(connection, "DELETE FROM RW_ATTACHMENT WHERE UPLOADER_ID = ?", userId);

                int deleted = preserveReplies ? anonymizeWithdrawnUser(connection, userId)
                        : executeUpdate(connection, "DELETE FROM RW_USER WHERE USER_ID = ?", userId);
                connection.commit();
                return deleted > 0;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int anonymizeWithdrawnUser(Connection connection, Long userId) throws SQLException {
        String identity = "withdrawn-" + java.util.UUID.randomUUID();
        String sql = """
                UPDATE RW_USER SET STATUS = 'WITHDRAWN', ROLE_CODE = 'MEMBER',
                    LOGIN_ID = ?, NICKNAME = ?, EMAIL = ?, PASSWORD_HASH = ?,
                    USER_NAME = '[탈퇴한 사용자]', PHONE = NULL, BIO = NULL, PROFILE_IMAGE_PATH = NULL,
                    SECURITY_QUESTION = NULL, SECURITY_ANSWER = NULL, EMAIL_VERIFIED_YN = 'N',
                    LAST_LOGIN_AT = NULL, UPDATED_AT = SYSTIMESTAMP
                WHERE USER_ID = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity);
            statement.setString(2, identity);
            statement.setString(3, identity + "@withdrawn.invalid");
            statement.setString(4, com.scuredweb.common.security.PasswordUtil.hash(java.util.UUID.randomUUID().toString()));
            statement.setLong(5, userId);
            return statement.executeUpdate();
        }
    }

    private void validateUserReferences(Connection connection) throws SQLException {
        Set<UserReference> supported = Set.of(
                new UserReference("RW_ACCESS_LOG", "USER_ID"),
                new UserReference("RW_ADMIN_ACTION_LOG", "ADMIN_USER_ID"),
                new UserReference("RW_ADMIN_USER_ROLE", "USER_ID"),
                new UserReference("RW_ATTACHMENT", "UPLOADER_ID"),
                new UserReference("RW_COMMENT", "USER_ID"),
                new UserReference("RW_ERROR_LOG", "USER_ID"),
                new UserReference("RW_GUESTBOOK", "OWNER_USER_ID"),
                new UserReference("RW_GUESTBOOK", "WRITER_USER_ID"),
                new UserReference("RW_MESSAGE", "RECEIVER_USER_ID"),
                new UserReference("RW_MESSAGE", "SENDER_USER_ID"),
                new UserReference("RW_NOTICE", "CREATED_BY"),
                new UserReference("RW_NOTIFICATION", "USER_ID"),
                new UserReference("RW_PASSWORD_RESET_TOKEN", "USER_ID"),
                new UserReference("RW_POST", "USER_ID"),
                new UserReference("RW_POST_REACTION", "USER_ID"),
                new UserReference("RW_RECENT_VIEW", "USER_ID"),
                new UserReference("RW_REPORT", "REPORTER_ID"),
                new UserReference("RW_USER_AUTH_LOG", "USER_ID")
        );
        Set<UserReference> unhandled = findUserReferences(connection);
        unhandled.removeAll(supported);
        if (!unhandled.isEmpty()) {
            throw new SQLException("Unhandled RW_USER foreign keys: " + unhandled);
        }
    }

    private Set<UserReference> findUserReferences(Connection connection) throws SQLException {
        String sql = """
                SELECT C.TABLE_NAME, CC.COLUMN_NAME
                  FROM USER_CONSTRAINTS C
                  JOIN USER_CONS_COLUMNS CC ON CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME
                  JOIN USER_CONSTRAINTS P ON P.CONSTRAINT_NAME = C.R_CONSTRAINT_NAME
                 WHERE C.CONSTRAINT_TYPE = 'R'
                   AND P.TABLE_NAME = 'RW_USER'
                """;
        Set<UserReference> references = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                references.add(new UserReference(
                        resultSet.getString("TABLE_NAME"),
                        resultSet.getString("COLUMN_NAME")));
            }
        }
        return references;
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM USER_TAB_COLUMNS
                 WHERE TABLE_NAME = ?
                   AND COLUMN_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private record UserReference(String tableName, String columnName) {
    }

    private void deletePostDependencies(Connection connection, Long userId, boolean preserveReplies) throws SQLException {
        String postIdSubQuery = "SELECT POST_ID FROM RW_POST WHERE USER_ID = ?";
        if (!preserveReplies) executeUpdate(connection,
                "DELETE FROM RW_REPORT WHERE TARGET_TYPE = 'POST' AND TARGET_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_RECENT_VIEW WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_POST_REACTION WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_ATTACHMENT WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_POST_TAG WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        if (preserveReplies) {
            // Retain the FK anchor so replies on this user's posts also survive withdrawal.
            executeUpdate(connection, "UPDATE RW_POST SET TITLE = '[삭제된 게시글입니다.]', CONTENT = '[삭제된 게시글입니다.]', UPDATED_AT = SYSTIMESTAMP WHERE USER_ID = ?", userId);
        } else {
            executeUpdate(connection, "DELETE FROM RW_COMMENT WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
            executeUpdate(connection, "DELETE FROM RW_POST WHERE USER_ID = ?", userId);
        }
    }

    private void deleteCommentTreeByUser(Connection connection, Long userId) throws SQLException {
        String sql = """
                DELETE FROM RW_COMMENT
                 WHERE COMMENT_ID IN (
                    SELECT COMMENT_ID
                      FROM RW_COMMENT
                     START WITH USER_ID = ?
                   CONNECT BY PRIOR COMMENT_ID = PARENT_COMMENT_ID
                 )
                """;
        executeUpdate(connection, sql, userId);
    }

    private int executeUpdate(Connection connection, String sql, Long userId) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            return preparedStatement.executeUpdate();
        }
    }

    public boolean updateProfileImage(Long userId, String profileImagePath) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET PROFILE_IMAGE_PATH = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, profileImagePath);
            preparedStatement.setLong(2, userId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public boolean existsProfileImageSavedName(String savedName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM RW_USER
                 WHERE STATUS = 'ACTIVE' AND PROFILE_IMAGE_PATH = ?
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, "/profile/image?savedName=" + savedName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    public boolean updateProfile(Long id, String nickname, String email, String phone) throws SQLException {
        return updateProfile(id, nickname, email, phone, null);
    }
    public boolean updateProfile(Long id, String nickname, String email, String phone, String password) throws SQLException {
        try (Connection c = DBUtil.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean changed;
                try (PreparedStatement s = c.prepareStatement("SELECT EMAIL, PASSWORD_HASH FROM RW_USER WHERE USER_ID = ? AND STATUS = 'ACTIVE' FOR UPDATE")) {
                    s.setLong(1, id);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) { c.rollback(); return false; }
                        changed = !java.util.Objects.equals(r.getString(1), email);
                        if (changed && !com.scuredweb.common.security.PasswordUtil.verifyPassword(password, r.getString(2))) { c.rollback(); return false; }
                    }
                }
                try (PreparedStatement s = c.prepareStatement("UPDATE RW_USER SET NICKNAME = ?, EMAIL = ?, PHONE = ?, EMAIL_VERIFIED_YN = CASE WHEN ? = 1 THEN 'N' ELSE EMAIL_VERIFIED_YN END, UPDATED_AT = SYSTIMESTAMP WHERE USER_ID = ?")) {
                    s.setString(1, nickname); s.setString(2, email); s.setString(3, phone); s.setInt(4, changed ? 1 : 0); s.setLong(5, id);
                    if (s.executeUpdate() != 1) { c.rollback(); return false; }
                }
                if (changed) try (PreparedStatement s = c.prepareStatement("DELETE FROM RW_PASSWORD_RESET_TOKEN WHERE USER_ID = ?")) { s.setLong(1,id); s.executeUpdate(); }
                c.commit(); return true;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        }
    }

    public boolean updatePassword(Long userId, String currentPassword, String newPassword) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET PASSWORD_HASH = ?, AUTH_VERSION = AUTH_VERSION + 1,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                   AND PASSWORD_HASH = ?
                   AND STATUS = 'ACTIVE'
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, newPassword);
            preparedStatement.setLong(2, userId);
            preparedStatement.setString(3, currentPassword);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public List<PostDTO> findPostsByUserId(Long userId) throws SQLException {
        String sql = """
                SELECT P.POST_ID, B.BOARD_NAME, NVL(C.CATEGORY_NAME, '일반') AS CATEGORY_NAME,
                       P.TITLE, P.CONTENT, U.NICKNAME, P.VIEW_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'LIKE') AS LIKE_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'DISLIKE') AS DISLIKE_COUNT,
                       P.COMMENT_COUNT,
                       P.NOTICE_YN, P.PINNED_YN, P.CREATED_AT
                  FROM RW_POST P
                  JOIN RW_BOARD B ON P.BOARD_ID = B.BOARD_ID
                  JOIN RW_USER U ON P.USER_ID = U.USER_ID
             LEFT JOIN RW_CATEGORY C ON P.CATEGORY_ID = C.CATEGORY_ID
                 WHERE P.USER_ID = ?
                 ORDER BY P.CREATED_AT DESC
                """;
        List<PostDTO> posts = new ArrayList<>();

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    PostDTO post = new PostDTO();
                    post.setPostId(resultSet.getLong("POST_ID"));
                    post.setBoardName(resultSet.getString("BOARD_NAME"));
                    post.setCategoryName(resultSet.getString("CATEGORY_NAME"));
                    post.setTitle(resultSet.getString("TITLE"));
                    post.setContent(resultSet.getString("CONTENT"));
                    post.setAuthorNickname(resultSet.getString("NICKNAME"));
                    post.setViewCount(resultSet.getInt("VIEW_COUNT"));
                    post.setLikeCount(resultSet.getInt("LIKE_COUNT"));
                    post.setDislikeCount(resultSet.getInt("DISLIKE_COUNT"));
                    post.setCommentCount(resultSet.getInt("COMMENT_COUNT"));
                    post.setNotice("Y".equals(resultSet.getString("NOTICE_YN")));
                    post.setPinned("Y".equals(resultSet.getString("PINNED_YN")));
                    posts.add(post);
                }
            }
        }
        return posts;
    }

    public List<CommentDTO> findCommentsByUserId(Long userId) throws SQLException {
        String sql = """
                SELECT C.COMMENT_ID, C.USER_ID, C.PARENT_COMMENT_ID, C.CONTENT, U.NICKNAME
                  FROM RW_COMMENT C
                  JOIN RW_USER U ON C.USER_ID = U.USER_ID
                 WHERE C.USER_ID = ?
                   AND C.STATUS = 'ACTIVE'
                 ORDER BY C.CREATED_AT DESC
                """;
        List<CommentDTO> comments = new ArrayList<>();

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    CommentDTO comment = new CommentDTO();
                    comment.setCommentId(resultSet.getLong("COMMENT_ID"));
                    comment.setUserId(resultSet.getLong("USER_ID"));
                    long parentId = resultSet.getLong("PARENT_COMMENT_ID");
                    comment.setParentCommentId(resultSet.wasNull() ? null : parentId);
                    comment.setAuthorNickname(resultSet.getString("NICKNAME"));
                    comment.setContent(resultSet.getString("CONTENT"));
                    comments.add(comment);
                }
            }
        }
        return comments;
    }

    public List<UserDTO> findAllUsers() throws SQLException {
        String sql = """
                SELECT USER_ID, LOGIN_ID, USER_NAME, NICKNAME, EMAIL, PHONE, PROFILE_IMAGE_PATH,
                       ROLE_CODE, STATUS, AUTH_VERSION, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
                  FROM RW_USER
                 ORDER BY USER_ID ASC
                """;
        List<UserDTO> users = new ArrayList<>();

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                UserDTO user = new UserDTO();
                user.setUserId(resultSet.getLong("USER_ID"));
                user.setLoginId(resultSet.getString("LOGIN_ID"));
                user.setUserName(resultSet.getString("USER_NAME"));
                user.setNickname(resultSet.getString("NICKNAME"));
                user.setEmail(resultSet.getString("EMAIL"));
                user.setPhone(resultSet.getString("PHONE"));
                user.setProfileImagePath(resultSet.getString("PROFILE_IMAGE_PATH"));
                user.setRoleCode(resultSet.getString("ROLE_CODE"));
                user.setStatus(resultSet.getString("STATUS"));
        user.setAuthVersion(resultSet.getLong("AUTH_VERSION"));
                user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
                users.add(user);
            }
        }
        return users;
    }

    public boolean updateUserStatus(Long userId, String status) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            boolean hasExpiryColumn = hasSuspensionExpiryColumn(connection);
            String sql = hasExpiryColumn
                    ? "UPDATE RW_USER SET STATUS = ?, SUSPENDED_UNTIL = CASE WHEN ? = 'ACTIVE' THEN NULL ELSE SUSPENDED_UNTIL END, UPDATED_AT = SYSTIMESTAMP WHERE USER_ID = ? AND ROLE_CODE = 'MEMBER'"
                    : "UPDATE RW_USER SET STATUS = ?, UPDATED_AT = SYSTIMESTAMP WHERE USER_ID = ? AND ROLE_CODE = 'MEMBER'";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, status);
                if (hasExpiryColumn) {
                    preparedStatement.setString(2, status);
                    preparedStatement.setLong(3, userId);
                } else {
                    preparedStatement.setLong(2, userId);
                }
                return preparedStatement.executeUpdate() > 0;
            }
        }
    }

    public boolean updateUserStatusByAdmin(Long userId, String status, Long adminUserId) throws SQLException {
        if (!("ACTIVE".equals(status) || "SUSPENDED".equals(status))) return false;
        String selectTargetSql = "SELECT ROLE_CODE, STATUS FROM RW_USER WHERE USER_ID = ? FOR UPDATE";
        String updateSql = """
                UPDATE RW_USER
                   SET STATUS = ?,
                       SUSPENDED_UNTIL = CASE WHEN ? = 'ACTIVE' THEN NULL ELSE SUSPENDED_UNTIL END,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                """;
        String auditSql = """
                INSERT INTO RW_ADMIN_ACTION_LOG (
                    ACTION_LOG_ID, ADMIN_USER_ID, ACTION_TYPE, TARGET_TYPE, TARGET_ID, ACTION_DETAIL, CREATED_AT
                ) VALUES (
                    SEQ_RW_ADMIN_ACTION_LOG.NEXTVAL, ?, 'USER_STATUS', 'USER', ?, ?, SYSTIMESTAMP
                )
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                com.scuredweb.common.security.AccountPolicy.lockAdministrators(connection);
                String roleCode;
                String currentStatus;
                try (PreparedStatement selectTarget = connection.prepareStatement(selectTargetSql)) {
                    selectTarget.setLong(1, userId);
                    try (ResultSet resultSet = selectTarget.executeQuery()) {
                        if (!resultSet.next()) {
                            connection.rollback();
                            return false;
                        }
                        roleCode = resultSet.getString("ROLE_CODE");
                        currentStatus = resultSet.getString("STATUS");
                    }
                }

                if (status.equals(currentStatus)) {
                    connection.rollback();
                    return false;
                }
                if ("SUSPENDED".equals(status)
                        && (!com.scuredweb.common.security.AccountPolicy.maySuspend(connection, userId, adminUserId, roleCode))) {
                    connection.rollback();
                    return false;
                }

                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setString(1, status);
                    update.setString(2, status);
                    update.setLong(3, userId);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement audit = connection.prepareStatement(auditSql)) {
                    audit.setLong(1, adminUserId);
                    audit.setLong(2, userId);
                    audit.setString(3, currentStatus + " -> " + status);
                    if (audit.executeUpdate() != 1) {
                        throw new SQLException("Administrator action log was not written.");
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean updateUserSuspension(Long userId, LocalDateTime suspendedUntil) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            if (!hasSuspensionExpiryColumn(connection)) {
                return updateUserStatus(userId, "SUSPENDED");
            }
            String sql = """
                    UPDATE RW_USER
                       SET STATUS = 'SUSPENDED',
                           SUSPENDED_UNTIL = ?,
                           UPDATED_AT = SYSTIMESTAMP
                     WHERE USER_ID = ? AND ROLE_CODE = 'MEMBER'
                    """;
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setTimestamp(1, Timestamp.valueOf(suspendedUntil));
                preparedStatement.setLong(2, userId);
                return preparedStatement.executeUpdate() > 0;
            }
        }
    }

    public boolean isUserSuspended(Long userId) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            boolean hasExpiryColumn = hasSuspensionExpiryColumn(connection);
            String sql = hasExpiryColumn
                    ? "SELECT STATUS, SUSPENDED_UNTIL FROM RW_USER WHERE USER_ID = ?"
                    : "SELECT STATUS FROM RW_USER WHERE USER_ID = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setLong(1, userId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next() || !"SUSPENDED".equals(resultSet.getString("STATUS"))) {
                        return false;
                    }
                    if (!hasExpiryColumn) {
                        return true;
                    }
                    Timestamp suspendedUntil = resultSet.getTimestamp("SUSPENDED_UNTIL");
                    return suspendedUntil == null || suspendedUntil.toLocalDateTime().isAfter(LocalDateTime.now());
                }
            }
        }
    }

    public boolean releaseExpiredSuspension(Long userId) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            if (!hasSuspensionExpiryColumn(connection)) {
                return false;
            }
            String sql = """
                    UPDATE RW_USER
                       SET STATUS = 'ACTIVE',
                           SUSPENDED_UNTIL = NULL,
                           UPDATED_AT = SYSTIMESTAMP
                     WHERE USER_ID = ?
                       AND STATUS = 'SUSPENDED'
                       AND SUSPENDED_UNTIL <= SYSTIMESTAMP
                    """;
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setLong(1, userId);
                return preparedStatement.executeUpdate() > 0;
            }
        }
    }

    private boolean hasSuspensionExpiryColumn(Connection connection) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM USER_TAB_COLUMNS
                 WHERE TABLE_NAME = 'RW_USER'
                   AND COLUMN_NAME = 'SUSPENDED_UNTIL'
                """;
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }
}

