package com.redweb.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UploadPathUtil {
    private static final Path ROOT = Path.of(System.getProperty("user.home"), "RedWebUploads");

    private UploadPathUtil() {
    }

    public static Path getRoot() throws IOException {
        Files.createDirectories(ROOT);
        return ROOT;
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
}
