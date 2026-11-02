package com.scuredweb.common.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@WebServlet("/assets/*")
public class AssetController extends HttpServlet {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico"
    );
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            ".css", "text/css",
            ".js", "application/javascript",
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".ico", "image/x-icon"
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (isInvalidAssetPath(pathInfo)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String resourcePath = toSafeResourcePath(pathInfo);
        if (resourcePath == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (InputStream inputStream = getServletContext().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String mimeType = getServletContext().getMimeType(resourcePath);
            if (mimeType == null) {
                mimeType = detectMimeType(resourcePath);
            }
            if (mimeType == null || "application/octet-stream".equals(mimeType)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setHeader("X-Content-Type-Options", "nosniff");
            if (isDevelopmentAsset(resourcePath)) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0L);
            }
            response.setContentType(mimeType);
            response.setCharacterEncoding("UTF-8");

            try (OutputStream outputStream = response.getOutputStream()) {
                inputStream.transferTo(outputStream);
            }
        }
    }

    private String detectMimeType(String resourcePath) {
        return MIME_BY_EXTENSION.get(extension(resourcePath));
    }

    private boolean isDevelopmentAsset(String resourcePath) {
        String extension = extension(resourcePath);
        return ".css".equals(extension) || ".js".equals(extension);
    }

    private boolean isInvalidAssetPath(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank()) {
            return true;
        }
        String normalizedInput = pathInfo.replace('\\', '/');
        String upper = normalizedInput.toUpperCase(Locale.ROOT);
        return normalizedInput.contains("..")
                || normalizedInput.contains("\0")
                || normalizedInput.contains("\r")
                || normalizedInput.contains("\n")
                || upper.contains("WEB-INF")
                || upper.contains("META-INF");
    }

    private String toSafeResourcePath(String pathInfo) {
        String normalizedInput = pathInfo.replace('\\', '/');
        String resourcePath = Path.of("/assets").resolve(normalizedInput.substring(1)).normalize().toString().replace('\\', '/');
        if (!resourcePath.startsWith("/assets/")) {
            return null;
        }
        return ALLOWED_EXTENSIONS.contains(extension(resourcePath)) ? resourcePath : null;
    }

    private String extension(String resourcePath) {
        if (resourcePath == null) {
            return "";
        }
        int dot = resourcePath.lastIndexOf('.');
        return dot < 0 ? "" : resourcePath.substring(dot).toLowerCase(Locale.ROOT);
    }
}

