package com.scuredweb.board.dao;

import com.scuredweb.board.dto.BoardDTO;
import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.board.dto.TagDTO;
import com.scuredweb.common.util.DBUtil;
import com.scuredweb.file.dto.AttachmentDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class BoardDAO {
    public List<PostDTO> findLatestPosts() throws SQLException {
        String sql = basePostQuery() + " WHERE P.STATUS = 'PUBLIC' AND B.ACTIVE_YN = 'Y' ORDER BY P.CREATED_AT DESC FETCH FIRST 8 ROWS ONLY";
        return findPosts(sql, null);
    }

    public List<PostDTO> findTrendingPosts() throws SQLException {
        String sql = basePostQuery() + " WHERE P.STATUS = 'PUBLIC' AND B.ACTIVE_YN = 'Y' ORDER BY LIKE_COUNT DESC, P.VIEW_COUNT DESC FETCH FIRST 5 ROWS ONLY";
        return findPosts(sql, null);
    }

    public List<PostDTO> findBoardPosts(String boardCode) throws SQLException {
        String sql = basePostQuery() + " WHERE B.BOARD_CODE = ? AND P.STATUS = 'PUBLIC' AND B.ACTIVE_YN = 'Y' ORDER BY P.PINNED_YN DESC, P.CREATED_AT DESC";
        return findPosts(sql, boardCode);
    }

    public List<PostDTO> searchBoardPosts(String boardCode, String keyword) throws SQLException {
        String sql = searchPostQueryLite()
                + " WHERE B.BOARD_CODE = ?"
                + " AND P.STATUS = 'PUBLIC' AND B.ACTIVE_YN = 'Y'"
                + " AND P.TITLE LIKE ? ESCAPE '\\'"
                + " ORDER BY P.CREATED_AT DESC";
        return querySearchPostsLite(sql, boardCode, likeKeyword(keyword));
    }

    public PostDTO findPostDetail(Long postId) throws SQLException {
        String sql = basePostQuery()
                + " WHERE P.STATUS = 'PUBLIC' AND B.ACTIVE_YN = 'Y'"
                + " AND P.POST_ID = ?";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
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
                 WHERE BOARD_CODE = ? AND ACTIVE_YN = 'Y'
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
                + " WHERE P.STATUS = 'PUBLIC' AND B.ACTIVE_YN = 'Y'"
                + " AND P.TITLE LIKE ? ESCAPE '\\'"
                + " ORDER BY P.CREATED_AT DESC";
        return querySearchPostsLite(sql, likeKeyword(keyword));
    }

    public Long insertPost(String boardCode, String title, String content, Long userId) throws SQLException {
        String boardSql = "SELECT BOARD_ID FROM RW_BOARD WHERE BOARD_CODE = ? AND ACTIVE_YN = 'Y' FOR UPDATE";
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
            } catch (SQLException | RuntimeException exception) {
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
                try {
                    long incoming = 0;
                    for (AttachmentDTO attachment : attachments) incoming = Math.addExact(incoming, attachment.getFileSize());
                    com.scuredweb.common.security.UploadBudget.checkQuota(connection, uploaderId, incoming, true);
                } catch (java.io.IOException exception) { throw new SQLException("Attachment quota rejected", exception); }
                if (!com.scuredweb.common.security.PostAccessPolicy.lockPublic(connection, postId)) { connection.rollback(); throw new SQLException("Post unavailable"); }
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
                        insertStatement.setString(9, attachment.getMimeType() == null ? "application/octet-stream" : attachment.getMimeType());
                        insertStatement.setString(10, attachment.isImage() ? "Y" : "N");
                        insertStatement.executeUpdate();
                    }

                    attachment.setAttachmentId(attachmentId);
                    attachment.setPostId(postId);
                }

                connection.commit();
            } catch (SQLException | RuntimeException exception) {
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
                   AND NOTICE_YN = 'N' AND STATUS = 'PUBLIC' AND BOARD_ID IN (SELECT BOARD_ID FROM RW_BOARD WHERE ACTIVE_YN = 'Y')
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
        String ownedPostSubquery = """
                SELECT POST_ID
                  FROM RW_POST
                 WHERE POST_ID = ?
                   AND USER_ID = ?
                   AND NOTICE_YN = 'N' AND STATUS = 'PUBLIC' AND BOARD_ID IN (SELECT BOARD_ID FROM RW_BOARD WHERE ACTIVE_YN = 'Y')
                """;
        String deletePostSql = """
                DELETE FROM RW_POST
                 WHERE POST_ID = ?
                   AND USER_ID = ?
                   AND NOTICE_YN = 'N' AND STATUS = 'PUBLIC' AND BOARD_ID IN (SELECT BOARD_ID FROM RW_BOARD WHERE ACTIVE_YN = 'Y')
                """;

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!com.scuredweb.common.security.PostAccessPolicy.lockPublic(connection, postId)) { connection.rollback(); return false; }
                deleteOwnedPostDependency(connection,
                        "DELETE FROM RW_RECENT_VIEW WHERE POST_ID IN (" + ownedPostSubquery + ")",
                        postId, ownerUserId);
                deleteOwnedPostDependency(connection,
                        "DELETE FROM RW_POST_REACTION WHERE POST_ID IN (" + ownedPostSubquery + ")",
                        postId, ownerUserId);
                deleteOwnedPostDependency(connection,
                        "DELETE FROM RW_POST_TAG WHERE POST_ID IN (" + ownedPostSubquery + ")",
                        postId, ownerUserId);
                deleteOwnedPostDependency(connection,
                        "DELETE FROM RW_COMMENT WHERE POST_ID IN (" + ownedPostSubquery + ")",
                        postId, ownerUserId);
                deleteOwnedPostDependency(connection,
                        "DELETE FROM RW_ATTACHMENT WHERE POST_ID IN (" + ownedPostSubquery + ")",
                        postId, ownerUserId);
                try (PreparedStatement postStatement = connection.prepareStatement(deletePostSql)) {
                    postStatement.setLong(1, postId);
                    postStatement.setLong(2, ownerUserId);
                    boolean deleted = postStatement.executeUpdate() > 0;
                    connection.commit();
                    return deleted;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean deletePostAsAdmin(Long postId) throws SQLException { return deletePostAsAdmin(postId,null); }
    public boolean deletePostAsAdmin(Long postId, Long actor) throws SQLException {
        String deletePostSql = "DELETE FROM RW_POST WHERE POST_ID = ?";

        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                deletePostDependency(connection, "DELETE FROM RW_RECENT_VIEW WHERE POST_ID = ?", postId);
                deletePostDependency(connection, "DELETE FROM RW_POST_REACTION WHERE POST_ID = ?", postId);
                deletePostDependency(connection, "DELETE FROM RW_POST_TAG WHERE POST_ID = ?", postId);
                deletePostDependency(connection, "DELETE FROM RW_COMMENT WHERE POST_ID = ?", postId);
                deletePostDependency(connection, "DELETE FROM RW_ATTACHMENT WHERE POST_ID = ?", postId);
                try (PreparedStatement postStatement = connection.prepareStatement(deletePostSql)) {
                    postStatement.setLong(1, postId);
                    boolean deleted = postStatement.executeUpdate() > 0;
                    if (deleted && actor != null) com.scuredweb.admin.dao.AdminAuditDAO.insertAction(connection,actor,"POST_DELETE","POST",postId,"Post deleted");
                    connection.commit();
                    return deleted;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean moderatePost(Long id, Long actor, boolean hide) throws SQLException {
        try (Connection c=DBUtil.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s=c.prepareStatement("UPDATE RW_POST SET STATUS = ?, UPDATED_AT = SYSTIMESTAMP WHERE POST_ID = ?")) {
                s.setString(1,hide?"HIDDEN":"PUBLIC"); s.setLong(2,id);
                if(s.executeUpdate()!=1) { c.rollback(); return false; }
                com.scuredweb.admin.dao.AdminAuditDAO.insertAction(c,actor,hide?"POST_HIDE":"POST_RESTORE","POST",id,"Post visibility changed");
                c.commit(); return true;
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
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

    public void incrementViewCount(Long postId) throws SQLException {
        String sql = "UPDATE RW_POST SET VIEW_COUNT = VIEW_COUNT + 1 WHERE POST_ID = ? AND STATUS = 'PUBLIC' AND BOARD_ID IN (SELECT BOARD_ID FROM RW_BOARD WHERE ACTIVE_YN = 'Y')";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            preparedStatement.executeUpdate();
        }
    }

    public String findReactionType(Long postId, Long userId) throws SQLException {
        String sql = "SELECT REACTION_TYPE FROM RW_POST_REACTION WHERE POST_ID = ? AND USER_ID = ?";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            preparedStatement.setLong(2, userId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("REACTION_TYPE") : null;
            }
        }
    }

    public int countReactions(Long postId, String reactionType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM RW_POST_REACTION WHERE POST_ID = ? AND REACTION_TYPE = ?";
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            preparedStatement.setString(2, reactionType);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public boolean upsertReaction(Long postId, Long userId, String reactionType) throws SQLException {
        try (Connection connection = DBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!com.scuredweb.common.security.PostAccessPolicy.lockPublic(connection, postId)) { connection.rollback(); return false; }
                try (PreparedStatement merge = connection.prepareStatement("""
                        MERGE INTO RW_POST_REACTION T
                        USING (
                            SELECT CAST(? AS NUMBER) POST_ID, CAST(? AS NUMBER) USER_ID, CAST(? AS VARCHAR2(20)) REACTION_TYPE FROM DUAL
                        ) S
                        ON (T.POST_ID = S.POST_ID AND T.USER_ID = S.USER_ID)
                        WHEN MATCHED THEN UPDATE SET
                            T.REACTION_TYPE = S.REACTION_TYPE,
                            T.CREATED_AT = SYSTIMESTAMP
                        WHEN NOT MATCHED THEN INSERT (
                            POST_ID, USER_ID, REACTION_TYPE, CREATED_AT
                        ) VALUES (
                            S.POST_ID, S.USER_ID, S.REACTION_TYPE, SYSTIMESTAMP
                        )
                        """)) {
                    merge.setLong(1, postId);
                    merge.setLong(2, userId);
                    merge.setString(3, reactionType);
                    merge.executeUpdate();
                }

                try (PreparedStatement likeUpdate = connection.prepareStatement("""
                        UPDATE RW_POST
                           SET LIKE_COUNT = (
                               SELECT COUNT(*)
                                 FROM RW_POST_REACTION
                                WHERE POST_ID = ? AND REACTION_TYPE = 'LIKE'
                           )
                         WHERE POST_ID = ?
                        """)) {
                    likeUpdate.setLong(1, postId);
                    likeUpdate.setLong(2, postId);
                    likeUpdate.executeUpdate();
                }

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

    public List<PostDTO> findAllPostsForAdmin() throws SQLException {
        String sql = """
                SELECT P.POST_ID, P.USER_ID, P.TITLE, P.CONTENT AS CONTENT, P.VIEW_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'LIKE') AS LIKE_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'DISLIKE') AS DISLIKE_COUNT,
                       P.COMMENT_COUNT,
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
        String boardSql = "SELECT BOARD_ID FROM RW_BOARD WHERE BOARD_CODE = ? AND ACTIVE_YN = 'Y' FOR UPDATE";
        String seqSql = "SELECT SEQ_RW_POST.NEXTVAL FROM DUAL";
        String insertSql = """
                INSERT INTO RW_POST (
                    POST_ID, BOARD_ID, CATEGORY_ID, USER_ID, TITLE, CONTENT,
                    VIEW_COUNT, LIKE_COUNT, COMMENT_COUNT, NOTICE_YN, PINNED_YN, STATUS
                ) VALUES (?, ?, NULL, ?, ?, ?, 0, 0, 0, 'Y', 'Y', 'PUBLIC')
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
            com.scuredweb.admin.dao.AdminAuditDAO.insertAction(connection,adminUserId,"NOTICE_CREATE","POST",postId,"Notice created");
            connection.commit();
            return postId;
            } catch(SQLException | RuntimeException e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private String basePostQuery() {
        return """
                SELECT P.POST_ID, P.USER_ID, P.TITLE, P.CONTENT AS CONTENT, P.VIEW_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'LIKE') AS LIKE_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'DISLIKE') AS DISLIKE_COUNT,
                       P.COMMENT_COUNT,
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
                       P.VIEW_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'LIKE') AS LIKE_COUNT,
                       (SELECT COUNT(*) FROM RW_POST_REACTION R WHERE R.POST_ID = P.POST_ID AND R.REACTION_TYPE = 'DISLIKE') AS DISLIKE_COUNT,
                       U.NICKNAME, B.BOARD_NAME
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

    private List<PostDTO> querySearchPostsLite(String sql, String... parameters) throws SQLException {
        List<PostDTO> posts = new ArrayList<>();
        try (Connection connection = DBUtil.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                preparedStatement.setString(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    posts.add(mapSearchPostLite(resultSet));
                }
            }
        }
        return posts;
    }

    private String likeKeyword(String keyword) {
        if (keyword == null) {
            return "%";
        }
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
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
        post.setBaseDislikeCount(resultSet.getInt("DISLIKE_COUNT"));
        post.setDislikeCount(resultSet.getInt("DISLIKE_COUNT"));
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
        post.setBaseLikeCount(resultSet.getInt("LIKE_COUNT"));
        post.setLikeCount(resultSet.getInt("LIKE_COUNT"));
        post.setBaseDislikeCount(resultSet.getInt("DISLIKE_COUNT"));
        post.setDislikeCount(resultSet.getInt("DISLIKE_COUNT"));
        post.setAuthorNickname(resultSet.getString("NICKNAME"));
        post.setBoardName(resultSet.getString("BOARD_NAME"));
        post.setStatus("PUBLIC");
        return post;
    }

    private List<AttachmentDTO> findAttachmentsByPostId(Connection connection, Long postId) throws SQLException {
        String sql = """
                SELECT ATTACH_ID, POST_ID, ORIGINAL_NAME, SAVED_NAME, FILE_PATH, FILE_SIZE, FILE_EXT, MIME_TYPE, IMAGE_YN
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
                    attachment.setFileExt(resultSet.getString("FILE_EXT"));
                    attachment.setMimeType(resultSet.getString("MIME_TYPE"));
                    attachment.setImage("Y".equals(resultSet.getString("IMAGE_YN")));
                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    private void deleteOwnedPostDependency(
            Connection connection, String sql, Long postId, Long ownerUserId) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            preparedStatement.setLong(2, ownerUserId);
            preparedStatement.executeUpdate();
        }
    }

    private void deletePostDependency(Connection connection, String sql, Long postId) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, postId);
            preparedStatement.executeUpdate();
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}

