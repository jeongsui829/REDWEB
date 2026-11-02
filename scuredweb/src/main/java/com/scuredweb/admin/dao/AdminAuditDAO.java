package com.scuredweb.admin.dao;

import com.scuredweb.common.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AdminAuditDAO {
    public boolean insertAction(Long adminUserId, String actionType, String targetType, Long targetId, String detail)
            throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            insertAction(connection, adminUserId, actionType, targetType, targetId, detail);
            return true;
        }
    }
    public static void insertAction(Connection connection, Long adminUserId, String actionType, String targetType, Long targetId, String detail) throws SQLException {
        String sql = """
                INSERT INTO RW_ADMIN_ACTION_LOG (
                    ACTION_LOG_ID, ADMIN_USER_ID, ACTION_TYPE, TARGET_TYPE, TARGET_ID, ACTION_DETAIL, CREATED_AT
                ) VALUES (
                    SEQ_RW_ADMIN_ACTION_LOG.NEXTVAL, ?, ?, ?, ?, ?, SYSTIMESTAMP
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, adminUserId);
            statement.setString(2, actionType);
            statement.setString(3, targetType);
            if (targetId == null) {
                statement.setNull(4, java.sql.Types.NUMERIC);
            } else {
                statement.setLong(4, targetId);
            }
            statement.setString(5, detail);
            if (statement.executeUpdate() != 1) throw new SQLException("Audit insert failed");
        }
    }
}
