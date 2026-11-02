package com.redweb.comment.service;

import com.redweb.comment.dao.CommentDAO;
import com.redweb.comment.dto.CommentDTO;
import com.redweb.common.constant.RoleCode;
import com.redweb.user.dto.UserDTO;
import java.util.List;

public class CommentService {
    private final CommentDAO commentDAO = new CommentDAO();

    public List<CommentDTO> getComments(Long postId) {
        try {
            return commentDAO.findCommentsByPostId(postId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public CommentDTO addComment(Long postId, Long userId, Long parentCommentId, String content) {
        try {
            return commentDAO.insertComment(postId, userId, parentCommentId, content);
        } catch (Exception exception) {
            return null;
        }
    }

    public boolean deleteComment(Long commentId, UserDTO loginUser) {
        if (commentId == null || loginUser == null || loginUser.getUserId() == null) {
            return false;
        }

        boolean admin = RoleCode.ADMIN.equals(loginUser.getRoleCode())
                || RoleCode.SUPER_ADMIN.equals(loginUser.getRoleCode());
        try {
            return commentDAO.deleteComment(commentId, loginUser.getUserId(), admin);
        } catch (Exception exception) {
            return false;
        }
    }
}
