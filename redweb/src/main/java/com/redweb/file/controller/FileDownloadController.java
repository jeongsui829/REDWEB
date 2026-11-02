package com.redweb.file.controller;

import com.redweb.common.util.UploadPathUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet("/files/download")
public class FileDownloadController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String savedName = request.getParameter("savedName");
        String originalName = request.getParameter("originalName");

        if (savedName == null || savedName.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Path filePath = UploadPathUtil.getAttachmentsDirectory().resolve(savedName);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String downloadName = originalName == null || originalName.isBlank() ? savedName : originalName;
        String encodedFileName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(resolveContentType(filePath));
        if (!isRenderableFile(filePath)) {
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        }
        response.setContentLengthLong(Files.size(filePath));

        try (InputStream inputStream = Files.newInputStream(filePath);
             OutputStream outputStream = response.getOutputStream()) {
            inputStream.transferTo(outputStream);
        }
    }

    private boolean isRenderableFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".html") || fileName.endsWith(".htm") || fileName.endsWith(".svg");
    }

    private String resolveContentType(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        }
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        String contentType = Files.probeContentType(filePath);
        return contentType != null ? contentType : "application/octet-stream";
    }
}
