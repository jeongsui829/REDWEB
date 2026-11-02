package com.scuredweb.comment.service;

import com.scuredweb.comment.dao.CommentDAO;
import com.scuredweb.comment.dto.CommentDTO;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.user.dto.UserDTO;
import java.util.List;

public class CommentService {
    private final CommentDAO commentDAO = new CommentDAO();

    public List<CommentDTO> getComments(Long postId) {
        try {
            return commentDAO.findCommentsByPostId(postId);
        } catch (Exception exception) {
            throw new DataAccessException("댓글을 불러오지 못했습니다.", exception);
        }
    }

    public CommentDTO addComment(Long postId, Long userId, Long parentCommentId, String content) {
        try {
            return commentDAO.insertComment(postId, userId, parentCommentId, content);
        } catch (Exception exception) {
            throw new DataAccessException("댓글을 등록하지 못했습니다.", exception);
        }
    }

    public boolean deleteComment(Long commentId, UserDTO loginUser) {
        if (commentId == null || loginUser == null || loginUser.getUserId() == null) {
            return false;
        }

        try {
            return commentDAO.deleteComment(commentId, loginUser.getUserId(), false);
        } catch (Exception exception) {
            throw new DataAccessException("댓글을 삭제하지 못했습니다.", exception);
        }
    }
}

