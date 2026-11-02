package com.scuredweb.comment.dao;

import com.scuredweb.comment.dto.CommentDTO;
import com.scuredweb.common.util.DBUtil;

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
                SELECT C.COMMENT_ID, C.USER_ID, C.PARENT_COMMENT_ID, C.CONTENT, C.STATUS, C.CREATED_AT, U.NICKNAME
                  FROM RW_COMMENT C
                  JOIN RW_USER U ON C.USER_ID = U.USER_ID
                 WHERE C.POST_ID = ?
                   AND C.STATUS IN ('ACTIVE', 'DELETED')
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
                    comment.setDeleted("DELETED".equals(resultSet.getString("STATUS")));
                    comment.setAuthorNickname(comment.isDeleted() ? "삭제된 사용자" : resultSet.getString("NICKNAME"));
                    comment.setContent(comment.isDeleted() ? "[삭제된 댓글입니다.]" : resultSet.getString("CONTENT"));
                    if (resultSet.getTimestamp("CREATED_AT") != null) {
                        comment.setCreatedAt(resultSet.getTimestamp("CREATED_AT").toLocalDateTime());
                    }

                    index.put(comment.getCommentId(), comment);
                }
            }
        }
        // Link only after reading every row: chronological IDs do not guarantee parent-first ordering.
        for (CommentDTO comment : index.values()) {
            CommentDTO parent = index.get(comment.getParentCommentId());
            if (parent == null) roots.add(comment);
            else parent.getChildren().add(comment);
        }
        return roots;
    }

    public CommentDTO insertComment(Long postId, Long userId, Long parentCommentId, String content) throws SQLException {
        String validateParentSql = """
                SELECT COUNT(*)
                  FROM RW_COMMENT
                 WHERE COMMENT_ID = ?
                   AND POST_ID = ?
                   AND STATUS IN ('ACTIVE', 'DELETED')
                """;
        String seqSql = "SELECT SEQ_RW_COMMENT.NEXTVAL FROM DUAL";
        String insertSql = """
                INSERT INTO RW_COMMENT (
                    COMMENT_ID, POST_ID, USER_ID, PARENT_COMMENT_ID, CONTENT, STATUS, CREATED_AT, UPDATED_AT
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        String updateCountSql = "UPDATE RW_POST SET COMMENT_COUNT = COMMENT_COUNT + 1 WHERE POST_ID = ?";
        String selectSql = """
                SELECT C.COMMENT_ID, C.USER_ID, C.PARENT_COMMENT_ID, C.CONTENT, C.STATUS, C.CREATED_AT, U.NICKNAME
                  FROM RW_COMMENT C
                  JOIN RW_USER U ON C.USER_ID = U.USER_ID
                 WHERE C.COMMENT_ID = ?
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!com.scuredweb.common.security.PostAccessPolicy.lockPublic(connection, postId)) { connection.rollback(); return null; }
                if (parentCommentId != null) {
                    try (PreparedStatement parentStatement = connection.prepareStatement(validateParentSql)) {
                        parentStatement.setLong(1, parentCommentId);
                        parentStatement.setLong(2, postId);
                        try (ResultSet resultSet = parentStatement.executeQuery()) {
                            resultSet.next();
                            if (resultSet.getInt(1) == 0) {
                                connection.rollback();
                                return null;
                            }
                        }
                    }
                }

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
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private boolean softDeleteOwnedComment(Connection connection, Long commentId, Long userId) throws SQLException {
        Long postId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT POST_ID FROM RW_COMMENT WHERE COMMENT_ID = ? AND USER_ID = ? AND STATUS = 'ACTIVE' FOR UPDATE")) {
            statement.setLong(1, commentId);
            statement.setLong(2, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return false;
                postId = rows.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE RW_COMMENT SET STATUS = 'DELETED', CONTENT = ?, UPDATED_AT = SYSTIMESTAMP WHERE COMMENT_ID = ? AND USER_ID = ? AND STATUS = 'ACTIVE'")) {
            statement.setString(1, "[삭제된 댓글입니다.]");
            statement.setLong(2, commentId);
            statement.setLong(3, userId);
            if (statement.executeUpdate() != 1) return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE RW_POST SET COMMENT_COUNT = GREATEST(COMMENT_COUNT - 1, 0) WHERE POST_ID = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
        return true;
    }

    /** Participates in the withdrawal transaction, preserving every foreign-owned reply and parent ID. */
    public void softDeleteCommentsByUser(Connection connection, Long userId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COMMENT_ID FROM RW_COMMENT WHERE USER_ID = ? AND STATUS = 'ACTIVE' ORDER BY COMMENT_ID")) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) ids.add(rows.getLong(1)); }
        }
        for (Long id : ids) softDeleteOwnedComment(connection, id, userId);
    }

    public boolean deleteComment(Long commentId, Long userId, boolean admin) throws SQLException {
        return deleteComment(commentId,userId,admin,false);
    }
    public boolean deleteCommentAudited(Long commentId, Long actor) throws SQLException {
        return deleteComment(commentId,actor,true,true);
    }
    private boolean deleteComment(Long commentId, Long userId, boolean admin, boolean audit) throws SQLException {
        if (!admin) {
            try (Connection connection = DBUtil.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Long postId = null;
                    try (PreparedStatement statement=connection.prepareStatement("SELECT POST_ID FROM RW_COMMENT WHERE COMMENT_ID = ? AND USER_ID = ?")) {
                        statement.setLong(1,commentId); statement.setLong(2,userId);
                        try(ResultSet rows=statement.executeQuery()) { if(rows.next()) postId=rows.getLong(1); }
                    }
                    if (!com.scuredweb.common.security.PostAccessPolicy.lockPublic(connection,postId)) { connection.rollback(); return false; }
                    boolean deleted = softDeleteOwnedComment(connection, commentId, userId);
                    connection.commit();
                    return deleted;
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                } finally { connection.setAutoCommit(true); }
            }
        }
        String postIdSql = admin
                ? "SELECT POST_ID FROM RW_COMMENT WHERE COMMENT_ID = ? AND STATUS IN ('ACTIVE', 'DELETED') FOR UPDATE"
                : "SELECT POST_ID FROM RW_COMMENT WHERE COMMENT_ID = ? AND USER_ID = ? AND STATUS = 'ACTIVE'";
        String countSql = """
                SELECT COUNT(CASE WHEN STATUS = 'ACTIVE' THEN 1 END)
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
                    updateStatement.setInt(1, deleteCount);
                    updateStatement.setLong(2, postId);
                    updateStatement.executeUpdate();
                }

                if (audit) com.scuredweb.admin.dao.AdminAuditDAO.insertAction(connection,userId,"COMMENT_DELETE","COMMENT",commentId,"Comment deleted");
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
}

