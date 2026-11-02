package com.scuredweb.board.service;

import com.scuredweb.board.dao.BoardDAO;
import com.scuredweb.board.dto.BoardDTO;
import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.board.dto.TagDTO;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.common.util.StoredFileCleanup;
import com.scuredweb.file.dto.AttachmentDTO;

import java.util.List;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BoardService {
    private static final Logger LOGGER = Logger.getLogger(BoardService.class.getName());
    private final BoardDAO boardDAO = new BoardDAO();

    public List<PostDTO> getLatestPosts() {
        try {
            return boardDAO.findLatestPosts();
        } catch (Exception exception) {
            throw readFailure("getLatestPosts", "", exception);
        }
    }

    public List<PostDTO> getTrendingPosts() {
        try {
            return boardDAO.findTrendingPosts();
        } catch (Exception exception) {
            throw readFailure("getTrendingPosts", "", exception);
        }
    }

    public List<BoardDTO> getBoards() {
        try {
            return boardDAO.findBoards();
        } catch (Exception exception) {
            throw readFailure("getBoards", "", exception);
        }
    }

    public List<TagDTO> getPopularTags() {
        try {
            return boardDAO.findPopularTags();
        } catch (Exception exception) {
            throw readFailure("getPopularTags", "", exception);
        }
    }

    public List<PostDTO> getBoardPosts(String boardCode) {
        try {
            return boardDAO.findBoardPosts(boardCode);
        } catch (Exception exception) {
            throw readFailure("getBoardPosts", ", boardCode=" + boardCode, exception);
        }
    }

    public List<PostDTO> searchBoardPosts(String boardCode, String keyword) {
        try {
            return boardDAO.searchBoardPosts(boardCode, keyword);
        } catch (Exception exception) {
            throw readFailure("searchBoardPosts", ", boardCode=" + boardCode, exception);
        }
    }

    public PostDTO getPostDetail(Long postId) {
        if (postId == null) {
            return null;
        }
        try {
            PostDTO post = boardDAO.findPostDetail(postId);
            if (post != null) {
                post.setDislikeCount(boardDAO.countReactions(postId, "DISLIKE"));
                return post;
            }
        } catch (Exception exception) {
            throw readFailure("getPostDetail", ", postId=" + postId, exception);
        }
        return null;
    }

    public BoardDTO getBoardByCode(String boardCode) {
        try {
            return boardDAO.findBoardByCode(boardCode);
        } catch (Exception exception) {
            throw readFailure("getBoardByCode", ", boardCode=" + boardCode, exception);
        }
    }

    public Long createPost(String boardCode, String title, String content, Long userId) {
        try {
            return boardDAO.insertPost(boardCode, title, content, userId);
        } catch (Exception exception) {
            throw writeFailure("createPost", ", boardCode=" + boardCode + ", userId=" + userId, exception);
        }
    }

    public boolean updatePost(Long postId, Long ownerUserId, String title, String content) {
        try {
            return boardDAO.updatePost(postId, ownerUserId, title, content);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean deletePost(Long postId, Long ownerUserId) {
        try {
            List<AttachmentDTO> attachments = boardDAO.findAttachmentsByPostId(postId);
            boolean deleted = boardDAO.deletePost(postId, ownerUserId);
            if (deleted) {
                StoredFileCleanup.deleteAttachments(
                        attachments.stream().map(AttachmentDTO::getSavedName).toList(),
                        "post-delete:" + postId);
            }
            return deleted;
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean deletePostAsAdmin(Long postId) { return deletePostAsAdmin(postId,null); }
    public boolean deletePostAsAdmin(Long postId, Long actor) {
        try {
            List<AttachmentDTO> attachments = boardDAO.findAttachmentsByPostId(postId);
            boolean deleted = boardDAO.deletePostAsAdmin(postId,actor);
            if (deleted) {
                StoredFileCleanup.deleteAttachments(
                        attachments.stream().map(AttachmentDTO::getSavedName).toList(),
                        "admin-post-delete:" + postId);
            }
            return deleted;
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean hidePost(Long postId) {
        try {
            return boardDAO.hidePost(postId);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean restorePost(Long postId) {
        try {
            return boardDAO.restorePost(postId);
        } catch (Exception exception) {
            return false;
        }
    }

    public List<PostDTO> getAllPostsForAdmin() {
        try {
            return boardDAO.findAllPostsForAdmin();
        } catch (Exception exception) {
            throw readFailure("getAllPostsForAdmin", "", exception);
        }
    }

    public Long createNotice(String boardCode, String title, String content, Long adminUserId) {
        try {
            return boardDAO.insertNoticePost(boardCode, title, content, adminUserId);
        } catch (Exception exception) {
            throw writeFailure("createNotice", ", boardCode=" + boardCode + ", userId=" + adminUserId, exception);
        }
    }

    public boolean saveAttachments(Long postId, Long uploaderId, List<AttachmentDTO> attachments) {
        try {
            boardDAO.insertAttachments(postId, uploaderId, attachments);
            return true;
        } catch (Exception exception) {
            logFailure("saveAttachments", ", postId=" + postId + ", userId=" + uploaderId, exception);
            return false;
        }
    }

    public List<AttachmentDTO> getAttachments(Long postId) {
        try {
            return boardDAO.findAttachmentsByPostId(postId);
        } catch (Exception exception) {
            throw readFailure("getAttachments", ", postId=" + postId, exception);
        }
    }

    public List<PostDTO> searchPosts(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        try {
            return boardDAO.searchPosts(InputValidator.limit(keyword, 100));
        } catch (Exception exception) {
            throw readFailure("searchPosts", "", exception);
        }
    }

    private DataAccessException readFailure(String feature, String context, Exception exception) {
        logFailure(feature, context, exception);
        return new DataAccessException("게시글 데이터를 불러오지 못했습니다.", exception);
    }

    private DataAccessException writeFailure(String feature, String context, Exception exception) {
        logFailure(feature, context, exception);
        return new DataAccessException("게시글 데이터를 저장하지 못했습니다.", exception);
    }

    private void logFailure(String feature, String context, Exception exception) {
        SQLException sqlException = findSqlException(exception);
        String sqlDetails = sqlException == null
                ? ""
                : ", oracleCode=" + sqlException.getErrorCode() + ", sqlState=" + sqlException.getSQLState();
        LOGGER.log(Level.SEVERE, feature + " failed" + context + sqlDetails, exception);
    }

    private SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    public void incrementViewCount(Long postId) {
        if (postId == null) {
            return;
        }
        try {
            boardDAO.incrementViewCount(postId);
        } catch (Exception exception) {
            logFailure("incrementViewCount", ", postId=" + postId, exception);
        }
    }

    public String getReactionType(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return null;
        }
        try {
            return boardDAO.findReactionType(postId, userId);
        } catch (Exception exception) {
            logFailure("getReactionType", ", postId=" + postId + ", userId=" + userId, exception);
            return null;
        }
    }

    public boolean reactToPost(Long postId, Long userId, String reactionType) {
        if (postId == null || userId == null || (!"LIKE".equals(reactionType) && !"DISLIKE".equals(reactionType))) {
            return false;
        }
        try {
            return boardDAO.upsertReaction(postId, userId, reactionType);
        } catch (Exception exception) {
            return false;
        }
    }
}

