package com.scuredweb.common.controller;

import com.scuredweb.common.util.DBUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/health")
public class HealthController extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(HealthController.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM DUAL");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new IllegalStateException("Database validation query returned no result.");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"UP\"}");
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "REDWEB health check failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("{\"status\":\"DOWN\"}");
        }
    }
}
