package com.redweb.social.dao;

import com.redweb.common.util.DBUtil;
import com.redweb.notification.dto.NotificationDTO;
import com.redweb.social.dto.GuestbookEntryDTO;
import com.redweb.social.dto.MessageDTO;
import com.redweb.social.dto.ReportDTO;
import com.redweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SocialDAO {
    public UserDTO findUser(String nicknameOrLoginId) throws SQLException {
        String sql = """
                SELECT USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL, PHONE,
                       PROFILE_IMAGE_PATH, BIO, ROLE_CODE, STATUS, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
                  FROM RW_USER
                 WHERE LOWER(NICKNAME) = LOWER(?)
                    OR LOWER(LOGIN_ID) = LOWER(?)
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, nicknameOrLoginId);
            preparedStatement.setString(2, nicknameOrLoginId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? mapUser(resultSet) : null;
            }
        }
    }

    public void updateBio(Long userId, String bio) throws SQLException {
        String sql = "UPDATE RW_USER SET BIO = ?, UPDATED_AT = SYSTIMESTAMP WHERE USER_ID = ?";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, bio);
            preparedStatement.setLong(2, userId);
            preparedStatement.executeUpdate();
        }
    }

    public List<MessageDTO> findMessages(Long userId) throws SQLException {
        String sql = """
                SELECT M.MESSAGE_ID, M.SENDER_USER_ID, SU.NICKNAME AS SENDER_NICKNAME,
                       M.RECEIVER_USER_ID, RU.NICKNAME AS RECEIVER_NICKNAME,
                       M.TITLE, M.CONTENT, M.READ_YN, M.CREATED_AT
                  FROM RW_MESSAGE M
                  JOIN RW_USER SU ON M.SENDER_USER_ID = SU.USER_ID
                  JOIN RW_USER RU ON M.RECEIVER_USER_ID = RU.USER_ID
                 WHERE M.RECEIVER_USER_ID = ?
                 ORDER BY M.CREATED_AT DESC
                """;
        List<MessageDTO> messages = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    MessageDTO message = new MessageDTO();
                    message.setMessageId(resultSet.getLong("MESSAGE_ID"));
                    message.setSenderUserId(resultSet.getLong("SENDER_USER_ID"));
                    message.setSenderNickname(resultSet.getString("SENDER_NICKNAME"));
                    message.setReceiverUserId(resultSet.getLong("RECEIVER_USER_ID"));
                    message.setReceiverNickname(resultSet.getString("RECEIVER_NICKNAME"));
                    message.setTitle(resultSet.getString("TITLE"));
                    message.setContent(resultSet.getString("CONTENT"));
                    message.setRead("Y".equals(resultSet.getString("READ_YN")));
                    message.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("CREATED_AT")));
                    messages.add(message);
                }
            }
        }
        return messages;
    }

    public long countUnreadMessages(Long userId) throws SQLException {
        return count("SELECT COUNT(*) FROM RW_MESSAGE WHERE RECEIVER_USER_ID = ? AND READ_YN = 'N'", userId);
    }

    public void markMessagesRead(Long userId) throws SQLException {
        execute("UPDATE RW_MESSAGE SET READ_YN = 'Y' WHERE RECEIVER_USER_ID = ?", userId);
    }

    public boolean deleteMessage(Long userId, Long messageId) throws SQLException {
        String sql = "DELETE FROM RW_MESSAGE WHERE MESSAGE_ID = ? AND RECEIVER_USER_ID = ?";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, messageId);
            preparedStatement.setLong(2, userId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public int deleteReadMessages(Long userId) throws SQLException {
        String sql = "DELETE FROM RW_MESSAGE WHERE RECEIVER_USER_ID = ? AND READ_YN = 'Y'";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            return preparedStatement.executeUpdate();
        }
    }

    public void insertMessage(UserDTO sender, UserDTO receiver, String title, String content) throws SQLException {
        String sql = """
                INSERT INTO RW_MESSAGE (MESSAGE_ID, SENDER_USER_ID, RECEIVER_USER_ID, TITLE, CONTENT, READ_YN, CREATED_AT)
                VALUES (SEQ_RW_MESSAGE.NEXTVAL, ?, ?, ?, ?, 'N', SYSTIMESTAMP)
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, sender.getUserId());
            preparedStatement.setLong(2, receiver.getUserId());
            preparedStatement.setString(3, title);
            preparedStatement.setString(4, content);
            preparedStatement.executeUpdate();
        }
    }

    public List<GuestbookEntryDTO> findGuestbookEntries(Long ownerUserId) throws SQLException {
        String sql = """
                SELECT G.ENTRY_ID, G.OWNER_USER_ID, OU.NICKNAME AS OWNER_NICKNAME,
                       G.WRITER_USER_ID, WU.NICKNAME AS WRITER_NICKNAME,
                       G.CONTENT, G.CREATED_AT
                  FROM RW_GUESTBOOK G
                  JOIN RW_USER OU ON G.OWNER_USER_ID = OU.USER_ID
                  JOIN RW_USER WU ON G.WRITER_USER_ID = WU.USER_ID
                 WHERE G.OWNER_USER_ID = ?
                 ORDER BY G.CREATED_AT DESC
                """;
        List<GuestbookEntryDTO> entries = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, ownerUserId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    GuestbookEntryDTO entry = new GuestbookEntryDTO();
                    entry.setEntryId(resultSet.getLong("ENTRY_ID"));
                    entry.setOwnerUserId(resultSet.getLong("OWNER_USER_ID"));
                    entry.setOwnerNickname(resultSet.getString("OWNER_NICKNAME"));
                    entry.setWriterUserId(resultSet.getLong("WRITER_USER_ID"));
                    entry.setWriterNickname(resultSet.getString("WRITER_NICKNAME"));
                    entry.setContent(resultSet.getString("CONTENT"));
                    entry.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("CREATED_AT")));
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public void insertGuestbookEntry(UserDTO writer, UserDTO owner, String content) throws SQLException {
        String sql = """
                INSERT INTO RW_GUESTBOOK (ENTRY_ID, OWNER_USER_ID, WRITER_USER_ID, CONTENT, CREATED_AT)
                VALUES (SEQ_RW_GUESTBOOK.NEXTVAL, ?, ?, ?, SYSTIMESTAMP)
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, owner.getUserId());
            preparedStatement.setLong(2, writer.getUserId());
            preparedStatement.setString(3, content);
            preparedStatement.executeUpdate();
        }
    }

    public List<NotificationDTO> findNotifications(Long userId) throws SQLException {
        String sql = """
                SELECT NOTI_ID, TITLE, MESSAGE, TARGET_URL, READ_YN, CREATED_AT
                  FROM RW_NOTIFICATION
                 WHERE USER_ID = ?
                 ORDER BY CREATED_AT DESC
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
                    notification.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("CREATED_AT")));
                    notifications.add(notification);
                }
            }
        }
        return notifications;
    }

    public long countUnreadNotifications(Long userId) throws SQLException {
        return count("SELECT COUNT(*) FROM RW_NOTIFICATION WHERE USER_ID = ? AND READ_YN = 'N'", userId);
    }

    public void markNotificationsRead(Long userId) throws SQLException {
        execute("UPDATE RW_NOTIFICATION SET READ_YN = 'Y' WHERE USER_ID = ?", userId);
    }

    public boolean deleteNotification(Long userId, Long notificationId) throws SQLException {
        String sql = "DELETE FROM RW_NOTIFICATION WHERE NOTI_ID = ? AND USER_ID = ?";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, notificationId);
            preparedStatement.setLong(2, userId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public int deleteReadNotifications(Long userId) throws SQLException {
        String sql = "DELETE FROM RW_NOTIFICATION WHERE USER_ID = ? AND READ_YN = 'Y'";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            return preparedStatement.executeUpdate();
        }
    }

    public void insertNotification(Long userId, String title, String message, String targetUrl) throws SQLException {
        String sql = """
                INSERT INTO RW_NOTIFICATION (NOTI_ID, USER_ID, NOTI_TYPE, TITLE, MESSAGE, TARGET_URL, READ_YN, CREATED_AT)
                VALUES (SEQ_RW_NOTIFICATION.NEXTVAL, ?, 'SOCIAL', ?, ?, ?, 'N', SYSTIMESTAMP)
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, title);
            preparedStatement.setString(3, message);
            preparedStatement.setString(4, targetUrl);
            preparedStatement.executeUpdate();
        }
    }

    public void insertReport(ReportDTO report) throws SQLException {
        String sql = """
                INSERT INTO RW_REPORT (
                    REPORT_ID, REPORTER_ID, TARGET_TYPE, TARGET_ID, REASON_CODE, REASON_DETAIL, STATUS, CREATED_AT
                ) VALUES (SEQ_RW_REPORT.NEXTVAL, ?, 'USER', ?, 'USER_REPORT', ?, 'PENDING', SYSTIMESTAMP)
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, report.getReporterUserId());
            preparedStatement.setLong(2, report.getTargetUserId());
            preparedStatement.setString(3, report.getReason());
            preparedStatement.executeUpdate();
        }
    }

    public List<ReportDTO> findReports() throws SQLException {
        String sql = """
                SELECT R.REPORT_ID, R.REPORTER_ID, R.TARGET_ID, RU.NICKNAME AS REPORTER_NICKNAME,
                       TU.NICKNAME AS TARGET_NICKNAME, R.REASON_DETAIL, R.STATUS,
                       AU.NICKNAME AS PROCESSED_BY_NICKNAME, R.PENALTY_SUMMARY,
                       R.CREATED_AT, R.PROCESSED_AT
                  FROM RW_REPORT R
                  JOIN RW_USER RU ON R.REPORTER_ID = RU.USER_ID
             LEFT JOIN RW_USER TU ON R.TARGET_ID = TU.USER_ID
             LEFT JOIN RW_USER AU ON R.PROCESSED_BY = AU.USER_ID
                 ORDER BY R.CREATED_AT DESC
                """;
        List<ReportDTO> reports = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                ReportDTO report = new ReportDTO();
                report.setReportId(resultSet.getLong("REPORT_ID"));
                report.setReporterUserId(resultSet.getLong("REPORTER_ID"));
                report.setTargetUserId(resultSet.getLong("TARGET_ID"));
                report.setReporterNickname(resultSet.getString("REPORTER_NICKNAME"));
                report.setTargetNickname(resultSet.getString("TARGET_NICKNAME"));
                report.setReason(resultSet.getString("REASON_DETAIL"));
                report.setStatus(resultSet.getString("STATUS"));
                report.setProcessedByNickname(resultSet.getString("PROCESSED_BY_NICKNAME"));
                report.setPenaltySummary(resultSet.getString("PENALTY_SUMMARY"));
                report.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("CREATED_AT")));
                report.setProcessedAt(toLocalDateTime(resultSet.getTimestamp("PROCESSED_AT")));
                reports.add(report);
            }
        }
        return reports;
    }

    public boolean updateReportStatus(Long reportId, Long adminUserId, String status, String penaltySummary) throws SQLException {
        String sql = """
                UPDATE RW_REPORT
                   SET STATUS = ?,
                       PROCESSED_BY = ?,
                       PROCESSED_AT = SYSTIMESTAMP,
                       PENALTY_SUMMARY = ?
                 WHERE REPORT_ID = ?
                   AND STATUS = 'PENDING'
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, status);
            preparedStatement.setLong(2, adminUserId);
            preparedStatement.setString(3, penaltySummary);
            preparedStatement.setLong(4, reportId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public int countResolvedReportsForTarget(Long targetUserId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM RW_REPORT
                 WHERE TARGET_TYPE = 'USER'
                   AND TARGET_ID = ?
                   AND STATUS = 'RESOLVED'
                """;
        return (int) count(sql, targetUserId);
    }

    private long count(String sql, Long id) throws SQLException {
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, id);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private void execute(String sql, Long id) throws SQLException {
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, id);
            preparedStatement.executeUpdate();
        }
    }

    private UserDTO mapUser(ResultSet resultSet) throws SQLException {
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
        user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
        user.setLastLoginAt(toLocalDateTime(resultSet.getTimestamp("LAST_LOGIN_AT")));
        user.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("CREATED_AT")));
        return user;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
