package com.redweb.comment.dao;

import com.redweb.comment.dto.CommentDTO;
import com.redweb.common.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommentDAO {
    public List<CommentDTO> findCommentsByPostId(Long postId) throws SQLException {
        String sql = """
                SELECT C.COMMENT_ID, C.USER_ID, C.PARENT_COMMENT_ID, C.CONTENT, C.CREATED_AT, U.NICKNAME
                  FROM RW_COMMENT C
                  JOIN RW_USER U ON C.USER_ID = U.USER_ID
                 WHERE C.POST_ID = ?
                   AND C.STATUS = 'ACTIVE'
                 ORDER BY NVL(C.PARENT_COMMENT_ID, C.COMMENT_ID), C.CREATED_AT
                """;
        Map<Long, CommentDTO> index = new LinkedHashMap<>();
        List<CommentDTO> roots = new ArrayList<>();

        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    CommentDTO comment = new CommentDTO();
                    comment.setCommentId(resultSet.getLong("COMMENT_ID"));
                    comment.setUserId(resultSet.getLong("USER_ID"));
                    long parentId = resultSet.getLong("PARENT_COMMENT_ID");
                    comment.setParentCommentId(resultSet.wasNull() ? null : parentId);
                    comment.setAuthorNickname(resultSet.getString("NICKNAME"));
                    comment.setContent(resultSet.getString("CONTENT"));
                    if (resultSet.getTimestamp("CREATED_AT") != null) {
                        comment.setCreatedAt(resultSet.getTimestamp("CREATED_AT").toLocalDateTime());
                    }

                    index.put(comment.getCommentId(), comment);
                    if (comment.getParentCommentId() == null) {
                        roots.add(comment);
                    } else if (index.containsKey(comment.getParentCommentId())) {
                        index.get(comment.getParentCommentId()).getChildren().add(comment);
                    }
                }
            }
        }
        return roots;
    }

    public CommentDTO insertComment(Long postId, Long userId, Long parentCommentId, String content) throws SQLException {
        String seqSql = "SELECT SEQ_RW_COMMENT.NEXTVAL FROM DUAL";
        String insertSql = """
                INSERT INTO RW_COMMENT (
                    COMMENT_ID, POST_ID, USER_ID, PARENT_COMMENT_ID, CONTENT, STATUS, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        String updateCountSql = "UPDATE RW_POST SET COMMENT_COUNT = COMMENT_COUNT + 1 WHERE POST_ID = ?";
        String selectSql = """
                SELECT C.COMMENT_ID, C.USER_ID, C.PARENT_COMMENT_ID, C.CONTENT, C.CREATED_AT, U.NICKNAME
                  FROM RW_COMMENT C
                  JOIN RW_USER U ON C.USER_ID = U.USER_ID
                 WHERE C.COMMENT_ID = ?
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long commentId;
                try (PreparedStatement seqStatement = connection.prepareStatement(seqSql);
                     ResultSet resultSet = seqStatement.executeQuery()) {
                    resultSet.next();
                    commentId = resultSet.getLong(1);
                }

                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    insertStatement.setLong(1, commentId);
                    insertStatement.setLong(2, postId);
                    insertStatement.setLong(3, userId);
                    if (parentCommentId == null) {
                        insertStatement.setNull(4, java.sql.Types.NUMERIC);
                    } else {
                        insertStatement.setLong(4, parentCommentId);
                    }
                    insertStatement.setString(5, content);
                    insertStatement.executeUpdate();
                }

                try (PreparedStatement updateStatement = connection.prepareStatement(updateCountSql)) {
                    updateStatement.setLong(1, postId);
                    updateStatement.executeUpdate();
                }

                try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                    selectStatement.setLong(1, commentId);
                    try (ResultSet resultSet = selectStatement.executeQuery()) {
                        resultSet.next();
                        CommentDTO comment = new CommentDTO();
                        comment.setCommentId(resultSet.getLong("COMMENT_ID"));
                        comment.setUserId(resultSet.getLong("USER_ID"));
                        long parentId = resultSet.getLong("PARENT_COMMENT_ID");
                        comment.setParentCommentId(resultSet.wasNull() ? null : parentId);
                        comment.setAuthorNickname(resultSet.getString("NICKNAME"));
                        comment.setContent(resultSet.getString("CONTENT"));
                        comment.setCreatedAt(resultSet.getTimestamp("CREATED_AT").toLocalDateTime());
                        connection.commit();
                        return comment;
                    }
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean deleteComment(Long commentId, Long userId, boolean admin) throws SQLException {
        String postIdSql = admin
                ? "SELECT POST_ID FROM RW_COMMENT WHERE COMMENT_ID = ? AND STATUS = 'ACTIVE'"
                : "SELECT POST_ID FROM RW_COMMENT WHERE COMMENT_ID = ? AND USER_ID = ? AND STATUS = 'ACTIVE'";
        String countSql = """
                SELECT COUNT(*)
                  FROM RW_COMMENT
                 START WITH COMMENT_ID = ?
               CONNECT BY PRIOR COMMENT_ID = PARENT_COMMENT_ID
                """;
        String deleteSql = """
                DELETE FROM RW_COMMENT
                 WHERE COMMENT_ID IN (
                    SELECT COMMENT_ID
                      FROM RW_COMMENT
                     START WITH COMMENT_ID = ?
                   CONNECT BY PRIOR COMMENT_ID = PARENT_COMMENT_ID
                 )
                """;
        String updateCountSql = "UPDATE RW_POST SET COMMENT_COUNT = GREATEST(COMMENT_COUNT - ?, 0) WHERE POST_ID = ?";

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long postId = null;
                try (PreparedStatement postStatement = connection.prepareStatement(postIdSql)) {
                    postStatement.setLong(1, commentId);
                    if (!admin) {
                        postStatement.setLong(2, userId);
                    }
                    try (ResultSet resultSet = postStatement.executeQuery()) {
                        if (resultSet.next()) {
                            postId = resultSet.getLong("POST_ID");
                        }
                    }
                }

                if (postId == null) {
                    connection.rollback();
                    return false;
                }

                int deleteCount;
                try (PreparedStatement countStatement = connection.prepareStatement(countSql)) {
                    countStatement.setLong(1, commentId);
                    try (ResultSet resultSet = countStatement.executeQuery()) {
                        resultSet.next();
                        deleteCount = resultSet.getInt(1);
                    }
                }

                if (deleteCount <= 0) {
                    connection.rollback();
                    return false;
                }

                int deleted;
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setLong(1, commentId);
                    deleted = deleteStatement.executeUpdate();
                }

                if (deleted <= 0) {
                    connection.rollback();
                    return false;
                }

                try (PreparedStatement updateStatement = connection.prepareStatement(updateCountSql)) {
                    updateStatement.setInt(1, deleted);
                    updateStatement.setLong(2, postId);
                    updateStatement.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
}
