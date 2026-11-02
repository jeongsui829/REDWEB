package com.scuredweb.admin.controller;

import com.scuredweb.admin.service.AdminDashboardService;
import com.scuredweb.board.service.BoardService;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.ViewUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/admin/dashboard")
public class AdminDashboardController extends HttpServlet {
    private final AdminDashboardService dashboardService = new AdminDashboardService();
    private final BoardService boardService = new BoardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("metrics", dashboardService.getMetrics());
        } catch (DataAccessException exception) {
            request.setAttribute("metrics", List.of());
            request.setAttribute("metricsLoadError", true);
        }
        try {
            request.setAttribute("recentPosts", boardService.getLatestPosts());
        } catch (DataAccessException exception) {
            request.setAttribute("recentPosts", List.of());
            request.setAttribute("recentPostsLoadError", true);
        }
        ViewUtil.forward(request, response, "admin/dashboard");
    }
}


