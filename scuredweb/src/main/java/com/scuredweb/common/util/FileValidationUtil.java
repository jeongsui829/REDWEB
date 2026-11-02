package com.scuredweb.common.util;

import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileValidationUtil {
    public static final long MAX_ATTACHMENT_SIZE = 10L * 1024L * 1024L;
    public static final long MAX_PROFILE_IMAGE_SIZE = 3L * 1024L * 1024L;
    public static final int MAX_ATTACHMENTS_PER_REQUEST = 10;
    public static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".pdf", ".txt", ".zip");
    public static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif");
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".gif", "image/gif",
            ".pdf", "application/pdf",
            ".txt", "text/plain",
            ".zip", "application/zip"
    );

    private FileValidationUtil() {
    }

    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = Path.of(fileName).getFileName().toString();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return normalized.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static String cleanOriginalName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "download";
        }
        return InputValidator.limit(Path.of(fileName).getFileName().toString().replace("\r", "").replace("\n", ""), 255);
    }

    public static boolean isAllowedAttachment(Part part) throws IOException {
        String extension = extension(part.getSubmittedFileName());
        return part.getSize() <= MAX_ATTACHMENT_SIZE
                && ATTACHMENT_EXTENSIONS.contains(extension)
                && hasExpectedMime(part, extension)
                && hasExpectedMagic(part, extension);
    }

    public static boolean isAllowedProfileImage(Part part) throws IOException {
        String extension = extension(part.getSubmittedFileName());
        return part.getSize() <= MAX_PROFILE_IMAGE_SIZE
                && IMAGE_EXTENSIONS.contains(extension)
                && hasExpectedMime(part, extension)
                && hasExpectedMagic(part, extension);
    }

    public static Path safeResolve(Path root, String savedName) throws IOException {
        Path normalizedRoot = root.toRealPath().normalize();
        Path resolved = normalizedRoot.resolve(savedName).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IOException("Invalid upload path");
        }
        return resolved;
    }

    public static String safeContentType(String extension) {
        return MIME_BY_EXTENSION.getOrDefault(extension == null ? "" : extension.toLowerCase(Locale.ROOT), "application/octet-stream");
    }

    private static boolean hasExpectedMime(Part part, String extension) {
        String expected = MIME_BY_EXTENSION.get(extension);
        if (expected == null) {
            return false;
        }
        String contentType = part.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return ".txt".equals(extension) || ".zip".equals(extension);
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (".txt".equals(extension)) {
            return normalized.startsWith("text/plain") || normalized.startsWith("application/octet-stream");
        }
        if (".zip".equals(extension)) {
            return normalized.startsWith("application/zip") || normalized.startsWith("application/x-zip-compressed")
                    || normalized.startsWith("application/octet-stream");
        }
        return normalized.startsWith(expected);
    }

    private static boolean hasExpectedMagic(Part part, String extension) throws IOException {
        byte[] header = new byte[8];
        int read;
        try (InputStream inputStream = part.getInputStream()) {
            read = inputStream.read(header);
        }
        if (read < 0) {
            read = 0;
        }
        return switch (extension) {
            case ".png" -> read >= 8 && header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
            case ".jpg", ".jpeg" -> read >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
            case ".gif" -> read >= 4 && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38;
            case ".pdf" -> read >= 4 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;
            case ".zip" -> read >= 4 && header[0] == 0x50 && header[1] == 0x4B;
            case ".txt" -> true;
            default -> false;
        };
    }
}

