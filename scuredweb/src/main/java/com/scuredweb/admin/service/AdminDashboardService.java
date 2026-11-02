package com.scuredweb.admin.service;

import com.scuredweb.admin.dao.AdminDashboardDAO;
import com.scuredweb.admin.dto.DashboardMetricDTO;
import com.scuredweb.common.exception.DataAccessException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminDashboardService {
    private static final Logger LOGGER = Logger.getLogger(AdminDashboardService.class.getName());
    private final AdminDashboardDAO dashboardDAO = new AdminDashboardDAO();

    public List<DashboardMetricDTO> getMetrics() {
        try {
            return dashboardDAO.loadMetrics();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "getMetrics failed", exception);
            throw new DataAccessException("관리자 통계를 불러오지 못했습니다.", exception);
        }
    }
}

