package com.scuredweb.file.controller;

import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.security.AuthorizationUtil;
import com.scuredweb.common.util.FileValidationUtil;
import com.scuredweb.common.util.UploadPathUtil;
import com.scuredweb.file.dao.FileDAO;
import com.scuredweb.file.dto.AttachmentDTO;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.user.service.UserService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet(urlPatterns = {"/files/image", "/profile/image"})
public class FileImageController extends HttpServlet {
    private final FileDAO fileDAO = new FileDAO();
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String savedName = request.getParameter("savedName");
        if (savedName == null || savedName.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String normalizedName = Path.of(savedName).getFileName().toString();
        String extension = FileValidationUtil.extension(normalizedName);
        if (!FileValidationUtil.IMAGE_EXTENSIONS.contains(extension)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if ("/profile/image".equals(request.getServletPath())) {
            serveProfileImage(response, normalizedName, extension);
            return;
        }

        serveAttachmentImage(request, response, normalizedName, extension);
    }

    private void serveAttachmentImage(HttpServletRequest request, HttpServletResponse response, String savedName, String extension)
            throws IOException {
        AttachmentDTO attachment;
        try {
            attachment = fileDAO.findAttachmentBySavedName(savedName);
        } catch (Exception exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (attachment == null || !attachment.isImage() || !canAccessAttachment(request, attachment)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!"PUBLIC".equals(attachment.getPostStatus())) {
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        serveFile(response, UploadPathUtil.getAttachmentsDirectory(), attachment.getSavedName(), extension);
    }

    private void serveProfileImage(HttpServletResponse response, String savedName, String extension) throws IOException {
        if (!userService.existsProfileImageSavedName(savedName)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        serveFile(response, UploadPathUtil.getProfilesDirectory(), savedName, extension);
    }

    private void serveFile(HttpServletResponse response, Path root, String savedName, String extension) throws IOException {
        Path filePath;
        try {
            filePath = FileValidationUtil.safeResolve(root, Path.of(savedName).getFileName().toString());
        } catch (IOException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(FileValidationUtil.safeContentType(extension));
        response.setContentLengthLong(Files.size(filePath));
        try (InputStream inputStream = Files.newInputStream(filePath);
             OutputStream outputStream = response.getOutputStream()) {
            inputStream.transferTo(outputStream);
        }
    }

    private boolean canAccessAttachment(HttpServletRequest request, AttachmentDTO attachment) {
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
}

