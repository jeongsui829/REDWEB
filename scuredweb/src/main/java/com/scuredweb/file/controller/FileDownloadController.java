package com.scuredweb.file.controller;

import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.security.AuthorizationUtil;
import com.scuredweb.common.util.FileValidationUtil;
import com.scuredweb.common.util.UploadPathUtil;
import com.scuredweb.file.dao.FileDAO;
import com.scuredweb.file.dto.AttachmentDTO;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet("/files/download")
public class FileDownloadController extends HttpServlet {
    private final FileDAO fileDAO = new FileDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String savedName = request.getParameter("savedName");

        if (savedName == null || savedName.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        AttachmentDTO attachment;
        try {
            attachment = fileDAO.findAttachmentBySavedName(Path.of(savedName).getFileName().toString());
        } catch (Exception exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (attachment == null || !canDownload(request, attachment)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (!"PUBLIC".equals(attachment.getPostStatus())) {
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        Path filePath;
        try {
            filePath = FileValidationUtil.safeResolve(UploadPathUtil.getAttachmentsDirectory(), attachment.getSavedName());
        } catch (IOException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String downloadName = FileValidationUtil.cleanOriginalName(attachment.getOriginalName());
        String encodedFileName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(safeContentType(attachment));
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        response.setContentLengthLong(Files.size(filePath));

        try (InputStream inputStream = Files.newInputStream(filePath);
             OutputStream outputStream = response.getOutputStream()) {
            inputStream.transferTo(outputStream);
        }
    }

    private boolean canDownload(HttpServletRequest request, AttachmentDTO attachment) {
        if ("PUBLIC".equals(attachment.getPostStatus())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        UserDTO loginUser = (UserDTO) session.getAttribute(SessionKey.LOGIN_USER);
        if (loginUser == null) {
            return false;
        }
        if (attachment.getUploaderId() != null && attachment.getUploaderId().equals(loginUser.getUserId())) {
            return true;
        }
        return AuthorizationUtil.isVerifiedAdmin(session, loginUser);
    }

    private String safeContentType(AttachmentDTO attachment) {
        String extension = attachment.getFileExt();
        if (extension != null && !extension.startsWith(".")) {
            extension = "." + extension;
        }
        return FileValidationUtil.safeContentType(extension);
    }
}

