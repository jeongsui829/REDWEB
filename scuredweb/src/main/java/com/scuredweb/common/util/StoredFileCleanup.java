package com.scuredweb.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StoredFileCleanup {
    private static final Logger LOGGER = Logger.getLogger(StoredFileCleanup.class.getName());

    private StoredFileCleanup() {
    }

    public static boolean deleteAttachments(Collection<String> savedNames, String stage) {
        return deleteFiles(savedNames, stage, true);
    }

    public static boolean deleteProfile(String savedName, String stage) {
        return savedName == null || deleteFiles(java.util.List.of(savedName), stage, false);
    }

    private static boolean deleteFiles(Collection<String> savedNames, String stage, boolean attachments) {
        boolean complete = true;
        if (savedNames == null) {
            return true;
        }
        for (String savedName : savedNames) {
            if (savedName == null || savedName.isBlank()) {
                continue;
            }
            try {
                Path root = attachments
                        ? UploadPathUtil.getAttachmentsDirectory()
                        : UploadPathUtil.getProfilesDirectory();
                Path file = FileValidationUtil.safeResolve(root, Path.of(savedName).getFileName().toString());
                if (!Files.deleteIfExists(file)) {
                    LOGGER.warning("Stored file was already absent. stage=" + stage + ", savedName=" + savedName);
                }
            } catch (IOException | RuntimeException exception) {
                complete = false;
                LOGGER.log(Level.WARNING,
                        "Stored file cleanup failed. stage=" + stage + ", savedName=" + savedName,
                        exception);
            }
        }
        return complete;
    }
}
