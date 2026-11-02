package com.redweb.board.dao;

import com.redweb.board.dto.BoardDTO;
import com.redweb.board.dto.PostDTO;
import com.redweb.board.dto.TagDTO;
import com.redweb.common.util.DBUtil;
import com.redweb.file.dto.AttachmentDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class BoardDAO {
    public List<PostDTO> findLatestPosts() throws SQLException {
        String sql = basePostQuery() + " WHERE P.STATUS = 'PUBLIC' ORDER BY P.CREATED_AT DESC FETCH FIRST 8 ROWS ONLY";
        return findPosts(sql, null);
    }

    public List<PostDTO> findTrendingPosts() throws SQLException {
        String sql = basePostQuery() + " WHERE P.STATUS = 'PUBLIC' ORDER BY P.LIKE_COUNT DESC, P.VIEW_COUNT DESC FETCH FIRST 5 ROWS ONLY";
        return findPosts(sql, null);
    }

    public List<PostDTO> findBoardPosts(String boardCode) throws SQLException {
        String sql = basePostQuery() + " WHERE B.BOARD_CODE = ? AND P.STATUS = 'PUBLIC' ORDER BY P.PINNED_YN DESC, P.CREATED_AT DESC";
        return findPosts(sql, boardCode);
    }

    public List<PostDTO> searchBoardPosts(String boardCode, String keyword) throws SQLException {
        String sql = searchPostQueryLite()
                + " WHERE B.BOARD_CODE = '" + boardCode + "'"
                + " AND P.STATUS = 'PUBLIC'"
                + " AND P.TITLE LIKE '%" + keyword + "%'"
                + " ORDER BY P.CREATED_AT DESC";
        return querySearchPostsLite(sql);
    }

    public PostDTO findPostDetail(String postIdExpression) throws SQLException {
        String sql = basePostQuery()
                + " WHERE P.STATUS = 'PUBLIC'"
                + " AND P.POST_ID = " + postIdExpression;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                PostDTO post = mapPost(resultSet);
                post.setAttachments(findAttachmentsByPostId(connection, post.getPostId()));
                return post;
            }
        }
    }

    public List<BoardDTO> findBoards() throws SQLException {
        String sql = """
                SELECT BOARD_ID, BOARD_CODE, BOARD_NAME, DESCRIPTION
                  FROM RW_BOARD
                 WHERE ACTIVE_YN = 'Y'
                 ORDER BY SORT_ORDER ASC
                """;
        List<BoardDTO> boards = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                BoardDTO board = new BoardDTO();
                board.setBoardId(resultSet.getLong("BOARD_ID"));
                board.setBoardCode(resultSet.getString("BOARD_CODE"));
                board.setBoardName(resultSet.getString("BOARD_NAME"));
                board.setDescription(resultSet.getString("DESCRIPTION"));
                boards.add(board);
            }
        }
        return boards;
    }

    public BoardDTO findBoardByCode(String boardCode) throws SQLException {
        String sql = """
                SELECT BOARD_ID, BOARD_CODE, BOARD_NAME, DESCRIPTION
                  FROM RW_BOARD
                 WHERE BOARD_CODE = ?
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, boardCode);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                BoardDTO board = new BoardDTO();
                board.setBoardId(resultSet.getLong("BOARD_ID"));
                board.setBoardCode(resultSet.getString("BOARD_CODE"));
                board.setBoardName(resultSet.getString("BOARD_NAME"));
                board.setDescription(resultSet.getString("DESCRIPTION"));
                return board;
            }
        }
    }

    public List<TagDTO> findPopularTags() throws SQLException {
        String sql = """
                SELECT TAG_ID, TAG_NAME, USE_COUNT
                  FROM RW_TAG
                 ORDER BY USE_COUNT DESC, TAG_NAME ASC
                 FETCH FIRST 12 ROWS ONLY
                """;
        List<TagDTO> tags = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                TagDTO tag = new TagDTO();
                tag.setTagId(resultSet.getLong("TAG_ID"));
                tag.setTagName(resultSet.getString("TAG_NAME"));
                tag.setUseCount(resultSet.getInt("USE_COUNT"));
                tags.add(tag);
            }
        }
        return tags;
    }

    public List<PostDTO> searchPosts(String keyword) throws SQLException {
        String sql = searchPostQueryLite()
                + " WHERE P.STATUS = 'PUBLIC'"
                + " AND P.TITLE LIKE '%" + keyword + "%'"
                + " ORDER BY P.CREATED_AT DESC";
        return querySearchPostsLite(sql);
    }

    public Long insertPost(String boardCode, String title, String content, Long userId) throws SQLException {
        String boardSql = "SELECT BOARD_ID FROM RW_BOARD WHERE BOARD_CODE = ?";
        String seqSql = "SELECT SEQ_RW_POST.NEXTVAL FROM DUAL";
        String insertSql = """
                INSERT INTO RW_POST (
                    POST_ID, BOARD_ID, CATEGORY_ID, USER_ID, TITLE, CONTENT,
                    VIEW_COUNT, LIKE_COUNT, COMMENT_COUNT, NOTICE_YN, PINNED_YN, STATUS
                ) VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 'N', 'N', 'PUBLIC')
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long boardId = null;
                try (PreparedStatement boardStatement = connection.prepareStatement(boardSql)) {
                    boardStatement.setString(1, boardCode);
                    try (ResultSet resultSet = boardStatement.executeQuery()) {
                        if (resultSet.next()) {
                            boardId = resultSet.getLong("BOARD_ID");
                        }
                    }
                }

                if (boardId == null) {
                    connection.rollback();
                    return null;
                }

                Long postId = null;
                try (PreparedStatement seqStatement = connection.prepareStatement(seqSql);
                     ResultSet resultSet = seqStatement.executeQuery()) {
                    if (resultSet.next()) {
                        postId = resultSet.getLong(1);
                    }
                }

                if (postId == null) {
                    connection.rollback();
                    return null;
                }

                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    insertStatement.setLong(1, postId);
                    insertStatement.setLong(2, boardId);
                    insertStatement.setNull(3, Types.NUMERIC);
                    insertStatement.setLong(4, userId);
                    insertStatement.setString(5, title);
                    insertStatement.setString(6, content);
                    insertStatement.executeUpdate();
                }

                connection.commit();
                return postId;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void insertAttachments(Long postId, Long uploaderId, List<AttachmentDTO> attachments) throws SQLException {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        String seqSql = "SELECT SEQ_RW_ATTACHMENT.NEXTVAL FROM DUAL";
        String insertSql = """
                INSERT INTO RW_ATTACHMENT (
                    ATTACH_ID, POST_ID, UPLOADER_ID, ORIGINAL_NAME, SAVED_NAME, FILE_PATH,
                    FILE_SIZE, FILE_EXT, MIME_TYPE, IMAGE_YN, CREATED_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (AttachmentDTO attachment : attachments) {
                    Long attachmentId;
                    try (PreparedStatement seqStatement = connection.prepareStatement(seqSql);
                         ResultSet resultSet = seqStatement.executeQuery()) {
                        resultSet.next();
                        attachmentId = resultSet.getLong(1);
                    }

                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                        insertStatement.setLong(1, attachmentId);
                        insertStatement.setLong(2, postId);
                        insertStatement.setLong(3, uploaderId);
                        insertStatement.setString(4, attachment.getOriginalName());
                        insertStatement.setString(5, attachment.getSavedName());
                        insertStatement.setString(6, attachment.getFilePath());
                        insertStatement.setLong(7, attachment.getFileSize());
                        insertStatement.setString(8, extractExtension(attachment.getOriginalName()));
                        insertStatement.setString(9, attachment.isImage() ? "image/*" : "application/octet-stream");
                        insertStatement.setString(10, attachment.isImage() ? "Y" : "N");
                        insertStatement.executeUpdate();
                    }

                    attachment.setAttachmentId(attachmentId);
                    attachment.setPostId(postId);
                }

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<AttachmentDTO> findAttachmentsByPostId(Long postId) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            return findAttachmentsByPostId(connection, postId);
        }
    }

    public boolean updatePost(Long postId, Long ownerUserId, String title, String content) throws SQLException {
        String sql = """
                UPDATE RW_POST
                   SET TITLE = ?, CONTENT = ?, UPDATED_AT = SYSTIMESTAMP
                 WHERE POST_ID = ?
                   AND USER_ID = ?
                   AND NOTICE_YN = 'N'
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, title);
            preparedStatement.setString(2, content);
            preparedStatement.setLong(3, postId);
            preparedStatement.setLong(4, ownerUserId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public boolean deletePost(Long postId, Long ownerUserId) throws SQLException {
        String deleteCommentsSql = """
                DELETE FROM RW_COMMENT
                 WHERE POST_ID IN (
                    SELECT POST_ID
                      FROM RW_POST
                     WHERE POST_ID = ?
                       AND USER_ID = ?
                       AND NOTICE_YN = 'N'
                 )
                """;
        String deleteAttachmentsSql = """
                DELETE FROM RW_ATTACHMENT
                 WHERE POST_ID IN (
                    SELECT POST_ID
                      FROM RW_POST
                     WHERE POST_ID = ?
                       AND USER_ID = ?
                       AND NOTICE_YN = 'N'
                 )
                """;
        String deletePostSql = """
                DELETE FROM RW_POST
                 WHERE POST_ID = ?
                   AND USER_ID = ?
                   AND NOTICE_YN = 'N'
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement commentStatement = connection.prepareStatement(deleteCommentsSql)) {
                    commentStatement.setLong(1, postId);
                    commentStatement.setLong(2, ownerUserId);
                    commentStatement.executeUpdate();
                }
                try (PreparedStatement attachmentStatement = connection.prepareStatement(deleteAttachmentsSql)) {
                    attachmentStatement.setLong(1, postId);
                    attachmentStatement.setLong(2, ownerUserId);
                    attachmentStatement.executeUpdate();
                }
                try (PreparedStatement postStatement = connection.prepareStatement(deletePostSql)) {
                    postStatement.setLong(1, postId);
                    postStatement.setLong(2, ownerUserId);
                    boolean deleted = postStatement.executeUpdate() > 0;
                    connection.commit();
                    return deleted;
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean deletePostAsAdmin(Long postId) throws SQLException {
        String deleteCommentsSql = "DELETE FROM RW_COMMENT WHERE POST_ID = ?";
        String deleteAttachmentsSql = "DELETE FROM RW_ATTACHMENT WHERE POST_ID = ?";
        String deletePostSql = "DELETE FROM RW_POST WHERE POST_ID = ?";

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement commentStatement = connection.prepareStatement(deleteCommentsSql)) {
                    commentStatement.setLong(1, postId);
                    commentStatement.executeUpdate();
                }
                try (PreparedStatement attachmentStatement = connection.prepareStatement(deleteAttachmentsSql)) {
                    attachmentStatement.setLong(1, postId);
                    attachmentStatement.executeUpdate();
                }
                try (PreparedStatement postStatement = connection.prepareStatement(deletePostSql)) {
                    postStatement.setLong(1, postId);
                    boolean deleted = postStatement.executeUpdate() > 0;
                    connection.commit();
                    return deleted;
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean hidePost(Long postId) throws SQLException {
        String sql = """
                UPDATE RW_POST
                   SET STATUS = 'HIDDEN',
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE POST_ID = ?
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public boolean restorePost(Long postId) throws SQLException {
        String sql = """
                UPDATE RW_POST
                   SET STATUS = 'PUBLIC',
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE POST_ID = ?
                """;
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            return preparedStatement.executeUpdate() > 0;
        }
    }

    public List<PostDTO> findAllPostsForAdmin() throws SQLException {
        String sql = """
                SELECT P.POST_ID, P.USER_ID, P.TITLE, P.CONTENT AS CONTENT, P.VIEW_COUNT, P.LIKE_COUNT, P.COMMENT_COUNT,
                       P.NOTICE_YN, P.PINNED_YN, P.STATUS, P.CREATED_AT, U.NICKNAME, B.BOARD_NAME, C.CATEGORY_NAME
                  FROM RW_POST P
                  JOIN RW_USER U ON P.USER_ID = U.USER_ID
                  JOIN RW_BOARD B ON P.BOARD_ID = B.BOARD_ID
             LEFT JOIN RW_CATEGORY C ON P.CATEGORY_ID = C.CATEGORY_ID
                 ORDER BY P.CREATED_AT DESC
                """;
        List<PostDTO> posts = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                posts.add(mapPost(resultSet));
            }
        }
        return posts;
    }

    public Long insertNoticePost(String boardCode, String title, String content, Long adminUserId) throws SQLException {
        String boardSql = "SELECT BOARD_ID FROM RW_BOARD WHERE BOARD_CODE = ?";
        String seqSql = "SELECT SEQ_RW_POST.NEXTVAL FROM DUAL";
        String insertSql = """
                INSERT INTO RW_POST (
                    POST_ID, BOARD_ID, CATEGORY_ID, USER_ID, TITLE, CONTENT,
                    VIEW_COUNT, LIKE_COUNT, COMMENT_COUNT, NOTICE_YN, PINNED_YN, STATUS
                ) VALUES (?, ?, NULL, ?, ?, ?, 0, 0, 0, 'Y', 'Y', 'PUBLIC')
                """;

        try (Connection connection = DBUtil.getConnection()) {
            Long boardId = null;
            try (PreparedStatement boardStatement = connection.prepareStatement(boardSql)) {
                boardStatement.setString(1, boardCode);
                try (ResultSet resultSet = boardStatement.executeQuery()) {
                    if (resultSet.next()) {
                        boardId = resultSet.getLong("BOARD_ID");
                    }
                }
            }

            if (boardId == null) {
                return null;
            }

            Long postId;
            try (PreparedStatement seqStatement = connection.prepareStatement(seqSql);
                 ResultSet resultSet = seqStatement.executeQuery()) {
                resultSet.next();
                postId = resultSet.getLong(1);
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setLong(1, postId);
                insertStatement.setLong(2, boardId);
                insertStatement.setLong(3, adminUserId);
                insertStatement.setString(4, title);
                insertStatement.setString(5, content);
                insertStatement.executeUpdate();
            }
            return postId;
        }
    }

    private String basePostQuery() {
        return """
                SELECT P.POST_ID, P.USER_ID, P.TITLE, P.CONTENT AS CONTENT, P.VIEW_COUNT, P.LIKE_COUNT, P.COMMENT_COUNT,
                       P.NOTICE_YN, P.PINNED_YN, P.STATUS, P.CREATED_AT, U.NICKNAME, B.BOARD_NAME, C.CATEGORY_NAME
                  FROM RW_POST P
                  JOIN RW_USER U ON P.USER_ID = U.USER_ID
                  JOIN RW_BOARD B ON P.BOARD_ID = B.BOARD_ID
             LEFT JOIN RW_CATEGORY C ON P.CATEGORY_ID = C.CATEGORY_ID
                """;
    }

    private String searchPostQueryLite() {
        return """
                SELECT P.POST_ID, P.USER_ID, P.TITLE, DBMS_LOB.SUBSTR(P.CONTENT, 1000, 1) AS CONTENT,
                       P.VIEW_COUNT, U.NICKNAME, B.BOARD_NAME
                  FROM RW_POST P
                  JOIN RW_USER U ON P.USER_ID = U.USER_ID
                  JOIN RW_BOARD B ON P.BOARD_ID = B.BOARD_ID
                """;
    }

    private List<PostDTO> findPosts(String sql, String boardCode) throws SQLException {
        List<PostDTO> posts = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (boardCode != null) {
                preparedStatement.setString(1, boardCode);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    posts.add(mapPost(resultSet));
                }
            }
        }
        return posts;
    }

    private List<PostDTO> queryPosts(String sql) throws SQLException {
        List<PostDTO> posts = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                posts.add(mapPost(resultSet));
            }
        }
        return posts;
    }

    private List<PostDTO> querySearchPostsLite(String sql) throws SQLException {
        List<PostDTO> posts = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                posts.add(mapSearchPostLite(resultSet));
            }
        }
        return posts;
    }

    private PostDTO mapPost(ResultSet resultSet) throws SQLException {
        PostDTO post = new PostDTO();
        post.setPostId(resultSet.getLong("POST_ID"));
        post.setUserId(resultSet.getLong("USER_ID"));
        post.setTitle(resultSet.getString("TITLE"));
        post.setContent(resultSet.getString("CONTENT"));
        post.setBaseViewCount(resultSet.getInt("VIEW_COUNT"));
        post.setViewCount(resultSet.getInt("VIEW_COUNT"));
        post.setBaseLikeCount(resultSet.getInt("LIKE_COUNT"));
        post.setLikeCount(resultSet.getInt("LIKE_COUNT"));
        post.setBaseDislikeCount(0);
        post.setDislikeCount(0);
        post.setCommentCount(resultSet.getInt("COMMENT_COUNT"));
        post.setNotice("Y".equals(resultSet.getString("NOTICE_YN")));
        post.setPinned("Y".equals(resultSet.getString("PINNED_YN")));
        post.setAuthorNickname(resultSet.getString("NICKNAME"));
        post.setBoardName(resultSet.getString("BOARD_NAME"));
        post.setCategoryName(resultSet.getString("CATEGORY_NAME"));
        if (resultSet.getTimestamp("CREATED_AT") != null) {
            post.setCreatedAt(resultSet.getTimestamp("CREATED_AT").toLocalDateTime());
        }
        try {
            post.setStatus(resultSet.getString("STATUS"));
        } catch (SQLException ignored) {
            post.setStatus("PUBLIC");
        }
        return post;
    }

    private PostDTO mapSearchPostLite(ResultSet resultSet) throws SQLException {
        PostDTO post = new PostDTO();
        post.setPostId(resultSet.getLong("POST_ID"));
        post.setUserId(resultSet.getLong("USER_ID"));
        post.setTitle(resultSet.getString("TITLE"));
        post.setContent(resultSet.getString("CONTENT"));
        post.setBaseViewCount(resultSet.getInt("VIEW_COUNT"));
        post.setViewCount(resultSet.getInt("VIEW_COUNT"));
        post.setBaseDislikeCount(0);
        post.setDislikeCount(0);
        post.setAuthorNickname(resultSet.getString("NICKNAME"));
        post.setBoardName(resultSet.getString("BOARD_NAME"));
        post.setStatus("PUBLIC");
        return post;
    }

    private List<AttachmentDTO> findAttachmentsByPostId(Connection connection, Long postId) throws SQLException {
        String sql = """
                SELECT ATTACH_ID, POST_ID, ORIGINAL_NAME, SAVED_NAME, FILE_PATH, FILE_SIZE, IMAGE_YN
                  FROM RW_ATTACHMENT
                 WHERE POST_ID = ?
                 ORDER BY ATTACH_ID ASC
                """;
        List<AttachmentDTO> attachments = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    AttachmentDTO attachment = new AttachmentDTO();
                    attachment.setAttachmentId(resultSet.getLong("ATTACH_ID"));
                    attachment.setPostId(resultSet.getLong("POST_ID"));
                    attachment.setOriginalName(resultSet.getString("ORIGINAL_NAME"));
                    attachment.setSavedName(resultSet.getString("SAVED_NAME"));
                    attachment.setFilePath(resultSet.getString("FILE_PATH"));
                    attachment.setFileSize(resultSet.getLong("FILE_SIZE"));
                    attachment.setImage("Y".equals(resultSet.getString("IMAGE_YN")));
                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
