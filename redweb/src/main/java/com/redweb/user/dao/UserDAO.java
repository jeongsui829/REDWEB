package com.redweb.user.dao;

import com.redweb.board.dto.PostDTO;
import com.redweb.comment.dto.CommentDTO;
import com.redweb.common.util.DBUtil;
import com.redweb.notification.dto.NotificationDTO;
import com.redweb.user.dto.LoginHistoryDTO;
import com.redweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    public UserDTO findById(Long userId) throws SQLException {
        String sql = """
                SELECT USER_ID, LOGIN_ID, USER_NAME, NICKNAME, EMAIL, PHONE, PROFILE_IMAGE_PATH,
                       ROLE_CODE, STATUS, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
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
                user.setUserName(resultSet.getString("USER_NAME"));
                user.setNickname(resultSet.getString("NICKNAME"));
                user.setEmail(resultSet.getString("EMAIL"));
                user.setPhone(resultSet.getString("PHONE"));
                user.setProfileImagePath(resultSet.getString("PROFILE_IMAGE_PATH"));
                user.setRoleCode(resultSet.getString("ROLE_CODE"));
                user.setStatus(resultSet.getString("STATUS"));
                user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
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

    public boolean withdrawUser(Long userId) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET STATUS = 'WITHDRAWN',
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                   AND STATUS = 'ACTIVE'
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public boolean deleteUserHard(Long userId) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM RW_NOTIFICATION WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_MESSAGE WHERE SENDER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_MESSAGE WHERE RECEIVER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_GUESTBOOK WHERE WRITER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_GUESTBOOK WHERE OWNER_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_USER_AUTH_LOG WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_RECENT_VIEW WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_ADMIN_USER_ROLE WHERE USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_ADMIN_ACTION_LOG WHERE ADMIN_USER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_REPORT WHERE REPORTER_ID = ?", userId);
                executeUpdate(connection, "DELETE FROM RW_REPORT WHERE TARGET_ID = ? AND TARGET_TYPE = 'USER'", userId);
                executeUpdate(connection, "DELETE FROM RW_NOTICE WHERE CREATED_BY = ?", userId);
                executeUpdate(connection, "UPDATE RW_ACCESS_LOG SET USER_ID = NULL WHERE USER_ID = ?", userId);
                executeUpdate(connection, "UPDATE RW_ERROR_LOG SET USER_ID = NULL WHERE USER_ID = ?", userId);

                deletePostDependencies(connection, userId);
                deleteCommentTreeByUser(connection, userId);
                executeUpdate(connection, "DELETE FROM RW_ATTACHMENT WHERE UPLOADER_ID = ?", userId);

                int deleted = executeUpdate(connection, "DELETE FROM RW_USER WHERE USER_ID = ?", userId);
                connection.commit();
                return deleted > 0;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void deletePostDependencies(Connection connection, Long userId) throws SQLException {
        String postIdSubQuery = "SELECT POST_ID FROM RW_POST WHERE USER_ID = ?";
        executeUpdate(connection, "DELETE FROM RW_RECENT_VIEW WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_ATTACHMENT WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_POST_TAG WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_COMMENT WHERE POST_ID IN (" + postIdSubQuery + ")", userId);
        executeUpdate(connection, "DELETE FROM RW_POST WHERE USER_ID = ?", userId);
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

    public boolean updateProfile(Long userId, String nickname, String email, String phone) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET NICKNAME = ?,
                       EMAIL = ?,
                       PHONE = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, nickname);
            preparedStatement.setString(2, email);
            preparedStatement.setString(3, phone);
            preparedStatement.setLong(4, userId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(Long userId, String currentPassword, String newPassword) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET PASSWORD_HASH = ?,
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
                       P.TITLE, P.CONTENT, U.NICKNAME, P.VIEW_COUNT, P.LIKE_COUNT, P.COMMENT_COUNT,
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
                       ROLE_CODE, STATUS, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
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
                user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
                users.add(user);
            }
        }
        return users;
    }

    public boolean updateUserStatus(Long userId, String status) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET STATUS = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, status);
            preparedStatement.setLong(2, userId);
            return preparedStatement.executeUpdate() > 0;
        }
    }
}
