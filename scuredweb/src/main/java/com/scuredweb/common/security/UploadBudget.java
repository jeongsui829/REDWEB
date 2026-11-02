package com.scuredweb.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public final class UploadBudget {
    private UploadBudget() {}

    public static long quotaBytes() {
        return setting("redweb.upload.quotaBytes", "REDWEB_UPLOAD_QUOTA_BYTES", 500L * 1024 * 1024);
    }

    private static long setting(String property, String env, long fallback) {
        String value = System.getProperty(property, System.getenv(env));
        long result = value == null ? fallback : Long.parseLong(value);
        if (result < 0) throw new IllegalArgumentException("Invalid upload setting: " + property);
        return result;
    }

    public static void validateConfiguration() {
        quotaBytes();
        setting("redweb.upload.minFreeBytes", "REDWEB_UPLOAD_MIN_FREE_BYTES", 1024L * 1024 * 1024);
        com.scuredweb.common.config.AppConfig.positiveSetting("upload.concurrency", 2, 16);
    }

    public static void checkCapacity(long used, long incoming, long limit) throws IOException {
        if (used < 0 || incoming < 0 || used > limit || incoming > limit - used)
            throw new IOException("Upload storage quota exceeded");
    }

    public static void checkSpace(Path directory, long incoming) throws IOException {
        long minimum = setting("redweb.upload.minFreeBytes", "REDWEB_UPLOAD_MIN_FREE_BYTES", 1024L * 1024 * 1024);
        long usable = Files.getFileStore(directory).getUsableSpace();
        if (incoming < 0 || usable < minimum || incoming > usable - minimum)
            throw new IOException("Insufficient free upload storage");
    }

    public static void checkQuota(Connection connection, Long userId, long incoming, boolean lock) throws SQLException, IOException {
        if (userId == null) throw new IOException("Upload requires an authenticated owner");
        if (lock) {
            try (var statement = connection.prepareStatement("SELECT USER_ID FROM RW_USER WHERE USER_ID = ? AND STATUS = 'ACTIVE' FOR UPDATE")) {
                statement.setLong(1, userId);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IOException("Upload owner unavailable");
                }
            }
        }
        try (var statement = connection.prepareStatement("SELECT NVL(SUM(FILE_SIZE), 0) FROM RW_ATTACHMENT WHERE UPLOADER_ID = ?")) {
            statement.setLong(1, userId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                checkCapacity(rows.getLong(1), incoming, quotaBytes());
            }
        }
    }
}
