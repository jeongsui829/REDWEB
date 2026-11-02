package com.redweb.admin.service;

import com.redweb.admin.dao.AdminDashboardDAO;
import com.redweb.admin.dto.DashboardMetricDTO;
import com.redweb.common.util.SampleDataFactory;

import java.util.List;

public class AdminDashboardService {
    private final AdminDashboardDAO dashboardDAO = new AdminDashboardDAO();

    public List<DashboardMetricDTO> getMetrics() {
        try {
            List<DashboardMetricDTO> metrics = dashboardDAO.loadMetrics();
            return metrics.isEmpty() ? SampleDataFactory.metrics() : metrics;
        } catch (Exception exception) {
            return SampleDataFactory.metrics();
        }
    }
}
