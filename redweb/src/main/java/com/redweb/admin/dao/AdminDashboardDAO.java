package com.redweb.admin.dao;

import com.redweb.admin.dto.DashboardMetricDTO;
import com.redweb.common.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardDAO {
    public List<DashboardMetricDTO> loadMetrics() throws Exception {
        List<DashboardMetricDTO> metrics = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection()) {
            metrics.add(new DashboardMetricDTO("오늘 가입", String.valueOf(count(connection,
                    "SELECT COUNT(*) FROM RW_USER WHERE TRUNC(CREATED_AT) = TRUNC(SYSDATE)")), ""));
            metrics.add(new DashboardMetricDTO("신규 게시글", String.valueOf(count(connection,
                    "SELECT COUNT(*) FROM RW_POST WHERE TRUNC(CREATED_AT) = TRUNC(SYSDATE)")), ""));
            metrics.add(new DashboardMetricDTO("미처리 신고", String.valueOf(count(connection,
                    "SELECT COUNT(*) FROM RW_REPORT WHERE STATUS = 'PENDING'")), ""));
            metrics.add(new DashboardMetricDTO("에러 로그", String.valueOf(count(connection,
                    "SELECT COUNT(*) FROM RW_ERROR_LOG WHERE TRUNC(CREATED_AT) = TRUNC(SYSDATE)")), ""));
        }
        return metrics;
    }

    private long count(Connection connection, String sql) throws Exception {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
