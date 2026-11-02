package com.scuredweb.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UploadPathUtil {
    public static void validateConfiguration() { resolveRoot(); }

    private UploadPathUtil() {
    }

    public static Path getRoot() throws IOException {
        Path root = resolveRoot();
        Files.createDirectories(root);
        return root;
    }

    public static Path getProfilesDirectory() throws IOException {
        Path directory = getRoot().resolve("profiles");
        Files.createDirectories(directory);
        return directory;
    }

    public static Path getAttachmentsDirectory() throws IOException {
        Path directory = getRoot().resolve("attachments");
        Files.createDirectories(directory);
        return directory;
    }

    private static Path resolveRoot() {
        String configured = System.getProperty("redweb.upload.root");
        if (configured == null) {
            configured = System.getenv("REDWEB_UPLOAD_ROOT");
        }
        if (configured == null && com.scuredweb.common.config.AppConfig.production()) throw new IllegalStateException("Production requires upload root");
        if (configured != null && (configured.isBlank() || !Path.of(configured).isAbsolute())) throw new IllegalStateException("Upload root must be absolute");
        Path root = configured == null
                ? Path.of(System.getProperty("user.home"), "RedWebUploads")
                : Path.of(configured.trim());
        return root.toAbsolutePath().normalize();
    }
}

