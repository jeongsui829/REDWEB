package com.scuredweb.auth.dao;

import com.scuredweb.auth.dto.LoginRequestDTO;
import com.scuredweb.auth.dto.RegisterRequestDTO;
import com.scuredweb.common.util.DBUtil;
import com.scuredweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class AuthDAO {
    public int releaseExpiredSuspensionByLoginId(String loginId) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET STATUS = 'ACTIVE',
                       SUSPENDED_UNTIL = NULL,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE LOGIN_ID = ?
                   AND STATUS = 'SUSPENDED'
                   AND SUSPENDED_UNTIL IS NOT NULL
                   AND SUSPENDED_UNTIL <= SYSTIMESTAMP
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, loginId);
            return preparedStatement.executeUpdate();
        }
    }

    public UserDTO findUserForLogin(LoginRequestDTO requestDTO) throws SQLException {
        String sql = userSelectSql()
                + " WHERE LOGIN_ID = ?"
                + "   AND STATUS = 'ACTIVE'";

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, requestDTO.getLoginId());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapUser(resultSet);
            }
        }
    }


    public UserDTO findUserByLoginId(String loginId) throws SQLException {
        String sql = userSelectSql() + " WHERE LOGIN_ID = ?";

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, loginId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapUser(resultSet);
            }
        }
    }

    public boolean existsConflict(String loginId, String nickname, String email) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM RW_USER
                 WHERE LOGIN_ID = ?
                    OR NICKNAME = ?
                    OR EMAIL = ?
                """;

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, loginId);
            preparedStatement.setString(2, nickname);
            preparedStatement.setString(3, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }


    public UserDTO insertUser(RegisterRequestDTO requestDTO) throws SQLException {
        String nextIdSql = "SELECT SEQ_RW_USER.NEXTVAL FROM DUAL";
        String insertSqlWithSecurity = """
                INSERT INTO RW_USER (
                    USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL,
                    PHONE, PROFILE_IMAGE_PATH, SECURITY_QUESTION, SECURITY_ANSWER,
                    STATUS, ROLE_CODE, EMAIL_VERIFIED_YN, CREATED_AT, UPDATED_AT
                ) VALUES (
                    ?, ?, ?, ?, ?, ?,
                    ?, '/assets/images/profile-default.png', ?, ?,
                    'ACTIVE', 'MEMBER', 'N', SYSTIMESTAMP, SYSTIMESTAMP
                )
                """;
        String insertSqlWithoutSecurity = """
                INSERT INTO RW_USER (
                    USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL,
                    PHONE, PROFILE_IMAGE_PATH, STATUS, ROLE_CODE, EMAIL_VERIFIED_YN, CREATED_AT, UPDATED_AT
                ) VALUES (
                    ?, ?, ?, ?, ?, ?,
                    ?, '/assets/images/profile-default.png',
                    'ACTIVE', 'MEMBER', 'N', SYSTIMESTAMP, SYSTIMESTAMP
                )
                """;

        try (Connection connection = DBUtil.getConnection()) {
            Long userId;
            try (PreparedStatement nextIdStatement = connection.prepareStatement(nextIdSql);
                 ResultSet resultSet = nextIdStatement.executeQuery()) {
                resultSet.next();
                userId = resultSet.getLong(1);
            }

            boolean hasSecurityColumns = hasColumn(connection, "RW_USER", "SECURITY_QUESTION")
                    && hasColumn(connection, "RW_USER", "SECURITY_ANSWER");
            String insertSql = hasSecurityColumns ? insertSqlWithSecurity : insertSqlWithoutSecurity;

            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setLong(1, userId);
                insertStatement.setString(2, requestDTO.getLoginId());
                insertStatement.setString(3, requestDTO.getPassword());
                insertStatement.setString(4, requestDTO.getUserName());
                insertStatement.setString(5, requestDTO.getNickname());
                insertStatement.setString(6, requestDTO.getEmail());
                insertStatement.setString(7, requestDTO.getPhone());
                if (hasSecurityColumns) {
                    insertStatement.setString(8, requestDTO.getSecurityQuestion());
                    insertStatement.setString(9, requestDTO.getSecurityAnswer());
                }
                insertStatement.executeUpdate();
            }

            UserDTO user = new UserDTO();
            user.setUserId(userId);
            user.setLoginId(requestDTO.getLoginId());
            user.setPasswordHash(requestDTO.getPassword());
            user.setUserName(requestDTO.getUserName());
            user.setNickname(requestDTO.getNickname());
            user.setEmail(requestDTO.getEmail());
            user.setPhone(requestDTO.getPhone());
            user.setProfileImagePath("/assets/images/profile-default.png");
            user.setRoleCode("MEMBER");
            user.setStatus("ACTIVE");
            user.setEmailVerified(false);
            return user;
        }
    }

    public String findLoginId(String userName, String email) throws SQLException {
        String sql = "SELECT LOGIN_ID FROM RW_USER WHERE USER_NAME = ? AND EMAIL = ? AND STATUS = 'ACTIVE' AND EMAIL_VERIFIED_YN = 'Y'";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, userName);
            preparedStatement.setString(2, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("LOGIN_ID") : null;
            }
        }
    }

    public Long findActiveUserId(String loginId, String email) throws SQLException {
        String sql = "SELECT USER_ID FROM RW_USER WHERE LOGIN_ID = ? AND EMAIL = ? AND STATUS = 'ACTIVE' AND EMAIL_VERIFIED_YN = 'Y'";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, loginId);
            preparedStatement.setString(2, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("USER_ID") : null;
            }
        }
    }

    public void replacePasswordResetToken(Long userId, String tokenHash, LocalDateTime expiresAt) throws SQLException {
        String revokeSql = """
                UPDATE RW_PASSWORD_RESET_TOKEN
                   SET USED_YN = 'Y',
                       USED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                   AND USED_YN = 'N'
                """;
        String insertSql = """
                INSERT INTO RW_PASSWORD_RESET_TOKEN (
                    TOKEN_ID, USER_ID, TOKEN_HASH, EXPIRES_AT, USED_YN, CREATED_AT
                ) VALUES (
                    SEQ_RW_PASSWORD_RESET_TOKEN.NEXTVAL, ?, ?, ?, 'N', SYSTIMESTAMP
                )
                """;
        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement revokeStatement = connection.prepareStatement(revokeSql);
                 PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                revokeStatement.setLong(1, userId);
                revokeStatement.executeUpdate();

                insertStatement.setLong(1, userId);
                insertStatement.setString(2, tokenHash);
                insertStatement.setTimestamp(3, Timestamp.valueOf(expiresAt));
                insertStatement.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean existsValidPasswordResetToken(String tokenHash) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM RW_PASSWORD_RESET_TOKEN
                 WHERE TOKEN_HASH = ?
                   AND USED_YN = 'N'
                   AND EXPIRES_AT > SYSTIMESTAMP
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tokenHash);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    public boolean resetPasswordWithToken(String tokenHash, String newPasswordHash) throws SQLException {
        String selectSql = """
                SELECT TOKEN_ID, USER_ID
                  FROM RW_PASSWORD_RESET_TOKEN
                 WHERE TOKEN_HASH = ?
                   AND USED_YN = 'N'
                   AND EXPIRES_AT > SYSTIMESTAMP
                 FOR UPDATE
                """;
        String updatePasswordSql = """
                UPDATE RW_USER
                   SET PASSWORD_HASH = ?, AUTH_VERSION = AUTH_VERSION + 1,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                   AND STATUS = 'ACTIVE'
                """;
        String markUsedSql = """
                UPDATE RW_PASSWORD_RESET_TOKEN
                   SET USED_YN = 'Y',
                       USED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ?
                   AND USED_YN = 'N'
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long userId;
                try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                    selectStatement.setString(1, tokenHash);
                    try (ResultSet resultSet = selectStatement.executeQuery()) {
                        if (!resultSet.next()) {
                            connection.rollback();
                            return false;
                        }
                        userId = resultSet.getLong("USER_ID");
                    }
                }

                try (PreparedStatement updatePasswordStatement = connection.prepareStatement(updatePasswordSql)) {
                    updatePasswordStatement.setString(1, newPasswordHash);
                    updatePasswordStatement.setLong(2, userId);
                    if (updatePasswordStatement.executeUpdate() <= 0) {
                        connection.rollback();
                        return false;
                    }
                }

                try (PreparedStatement markUsedStatement = connection.prepareStatement(markUsedSql)) {
                    markUsedStatement.setLong(1, userId);
                    markUsedStatement.executeUpdate();
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

    public void updatePasswordHash(Long userId, String passwordHash) throws SQLException {
        String sql = """
                UPDATE RW_USER
                   SET PASSWORD_HASH = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE USER_ID = ? AND STATUS = 'ACTIVE' AND PASSWORD_HASH NOT LIKE 'pbkdf2$%'
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, passwordHash);
            preparedStatement.setLong(2, userId);
            if (preparedStatement.executeUpdate() != 1) throw new SQLException("Legacy credential changed concurrently");
        }
    }

    public boolean upgradePasswordHash(Long id, String oldHash, String newHash) throws SQLException {
        try (Connection c = DBUtil.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE RW_USER SET PASSWORD_HASH = ? WHERE USER_ID = ? AND PASSWORD_HASH = ? AND STATUS = 'ACTIVE'")) {
            s.setString(1, newHash); s.setLong(2, id); s.setString(3, oldHash);
            return s.executeUpdate() == 1;
        }
    }

    public void recordSuccessfulLogin(Long userId, String loginIp, String userAgent) throws SQLException {
        String updateUserSql = "UPDATE RW_USER SET LAST_LOGIN_AT = SYSTIMESTAMP, UPDATED_AT = SYSTIMESTAMP WHERE USER_ID = ?";
        String insertLogSql = """
                INSERT INTO RW_USER_AUTH_LOG (
                    AUTH_LOG_ID, USER_ID, LOGIN_IP, USER_AGENT, LOGIN_RESULT, LOGIN_AT
                ) VALUES (
                    SEQ_RW_USER_AUTH_LOG.NEXTVAL, ?, ?, ?, 'SUCCESS', SYSTIMESTAMP
                )
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement updateUser = connection.prepareStatement(updateUserSql);
                 PreparedStatement insertLog = connection.prepareStatement(insertLogSql)) {
                updateUser.setLong(1, userId);
                if (updateUser.executeUpdate() != 1) {
                    throw new SQLException("Authenticated user no longer exists.");
                }

                insertLog.setLong(1, userId);
                insertLog.setString(2, bounded(loginIp, 64));
                insertLog.setString(3, bounded(userAgent, 500));
                insertLog.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM USER_TAB_COLUMNS
                 WHERE TABLE_NAME = ?
                   AND COLUMN_NAME = ?
                """;
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, columnName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private String userSelectSql() {
        return """
                SELECT USER_ID, LOGIN_ID, PASSWORD_HASH, USER_NAME, NICKNAME, EMAIL, PHONE,
                       PROFILE_IMAGE_PATH, ROLE_CODE, STATUS, AUTH_VERSION, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
                  FROM RW_USER
                """;
    }

    private String bounded(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
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
        user.setRoleCode(resultSet.getString("ROLE_CODE"));
        user.setStatus(resultSet.getString("STATUS"));
        user.setAuthVersion(resultSet.getLong("AUTH_VERSION"));
        user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
        return user;
    }
}

