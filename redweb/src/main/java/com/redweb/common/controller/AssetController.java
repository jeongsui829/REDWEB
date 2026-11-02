package com.redweb.common.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@WebServlet("/assets/*")
public class AssetController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String resourcePath = "/assets" + pathInfo;
        try (InputStream inputStream = getServletContext().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String mimeType = getServletContext().getMimeType(resourcePath);
            if (mimeType == null) {
                mimeType = detectMimeType(resourcePath);
            }

            response.setContentType(mimeType);
            response.setCharacterEncoding("UTF-8");

            try (OutputStream outputStream = response.getOutputStream()) {
                inputStream.transferTo(outputStream);
            }
        }
    }

    private String detectMimeType(String resourcePath) {
        if (resourcePath.endsWith(".css")) {
            return "text/css";
        }
        if (resourcePath.endsWith(".js")) {
            return "application/javascript";
        }
        if (resourcePath.endsWith(".png")) {
            return "image/png";
        }
        if (resourcePath.endsWith(".jpg") || resourcePath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (resourcePath.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
