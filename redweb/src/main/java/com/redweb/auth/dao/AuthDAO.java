package com.redweb.auth.dao;

import com.redweb.auth.dto.LoginRequestDTO;
import com.redweb.auth.dto.RegisterRequestDTO;
import com.redweb.common.util.DBUtil;
import com.redweb.user.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AuthDAO {
    public UserDTO findUserForLogin(LoginRequestDTO requestDTO) throws SQLException {
        String sql = userSelectSql()
                + " WHERE LOGIN_ID = '" + requestDTO.getLoginId() + "'"
                + "   AND PASSWORD_HASH = '" + requestDTO.getPassword() + "'"
                + "   AND STATUS = 'ACTIVE'";

        try (Connection connection = DBUtil.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapUser(resultSet);
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
        String sql = "SELECT LOGIN_ID FROM RW_USER WHERE USER_NAME = ? AND EMAIL = ? AND STATUS = 'ACTIVE'";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, userName);
            preparedStatement.setString(2, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("LOGIN_ID") : null;
            }
        }
    }

    public String findSecurityQuestion(String loginId, String email) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            if (!hasColumn(connection, "RW_USER", "SECURITY_QUESTION")) {
                return null;
            }
        }
        String sql = "SELECT SECURITY_QUESTION FROM RW_USER WHERE LOGIN_ID = ? AND EMAIL = ? AND STATUS = 'ACTIVE'";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, loginId);
            preparedStatement.setString(2, email);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("SECURITY_QUESTION") : null;
            }
        }
    }

    public boolean resetPassword(String loginId, String email, String securityAnswer, String newPassword) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            if (!hasColumn(connection, "RW_USER", "SECURITY_ANSWER")) {
                return false;
            }
        }
        String sql = """
                UPDATE RW_USER
                   SET PASSWORD_HASH = ?,
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE LOGIN_ID = ?
                   AND EMAIL = ?
                   AND LOWER(SECURITY_ANSWER) = LOWER(?)
                   AND STATUS = 'ACTIVE'
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, newPassword);
            preparedStatement.setString(2, loginId);
            preparedStatement.setString(3, email);
            preparedStatement.setString(4, securityAnswer);
            return preparedStatement.executeUpdate() > 0;
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
                       PROFILE_IMAGE_PATH, ROLE_CODE, STATUS, EMAIL_VERIFIED_YN, LAST_LOGIN_AT, CREATED_AT
                  FROM RW_USER
                """;
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
        user.setEmailVerified("Y".equals(resultSet.getString("EMAIL_VERIFIED_YN")));
        return user;
    }
}
