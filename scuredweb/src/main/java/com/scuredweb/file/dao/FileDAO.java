package com.scuredweb.file.dao;

import com.scuredweb.common.util.DBUtil;
import com.scuredweb.file.dto.AttachmentDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FileDAO {
    public AttachmentDTO findAttachmentBySavedName(String savedName) throws SQLException {
        String sql = """
                SELECT A.ATTACH_ID, A.POST_ID, A.UPLOADER_ID, A.ORIGINAL_NAME, A.SAVED_NAME,
                       A.FILE_PATH, A.FILE_SIZE, A.FILE_EXT, A.MIME_TYPE, A.IMAGE_YN,
                       CASE WHEN B.ACTIVE_YN = 'Y' THEN P.STATUS ELSE 'HIDDEN' END AS POST_STATUS
                  FROM RW_ATTACHMENT A
                  JOIN RW_POST P ON A.POST_ID = P.POST_ID
                  JOIN RW_BOARD B ON P.BOARD_ID = B.BOARD_ID
                 WHERE A.SAVED_NAME = ?
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, savedName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                AttachmentDTO attachment = new AttachmentDTO();
                attachment.setAttachmentId(resultSet.getLong("ATTACH_ID"));
                attachment.setPostId(resultSet.getLong("POST_ID"));
                attachment.setUploaderId(resultSet.getLong("UPLOADER_ID"));
                attachment.setOriginalName(resultSet.getString("ORIGINAL_NAME"));
                attachment.setSavedName(resultSet.getString("SAVED_NAME"));
                attachment.setFilePath(resultSet.getString("FILE_PATH"));
                attachment.setFileSize(resultSet.getLong("FILE_SIZE"));
                attachment.setFileExt(resultSet.getString("FILE_EXT"));
                attachment.setMimeType(resultSet.getString("MIME_TYPE"));
                attachment.setImage("Y".equals(resultSet.getString("IMAGE_YN")));
                attachment.setPostStatus(resultSet.getString("POST_STATUS"));
                return attachment;
            }
        }
    }
}

