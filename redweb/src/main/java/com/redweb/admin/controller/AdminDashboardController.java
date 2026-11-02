package com.redweb.admin.controller;

import com.redweb.admin.service.AdminDashboardService;
import com.redweb.board.service.BoardService;
import com.redweb.common.util.ViewUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/admin/dashboard")
public class AdminDashboardController extends HttpServlet {
    private final AdminDashboardService dashboardService = new AdminDashboardService();
    private final BoardService boardService = new BoardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("metrics", dashboardService.getMetrics());
        request.setAttribute("recentPosts", boardService.getLatestPosts());
        ViewUtil.forward(request, response, "admin/dashboard");
    }
}

