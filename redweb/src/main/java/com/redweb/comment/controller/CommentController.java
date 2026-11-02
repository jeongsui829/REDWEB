package com.redweb.comment.controller;

import com.redweb.board.service.BoardService;
import com.redweb.comment.service.CommentService;
import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.RequestUtil;
import com.redweb.social.service.SocialService;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet({"/comment/add", "/comment/delete"})
public class CommentController extends HttpServlet {
    private final BoardService boardService = new BoardService();
    private final CommentService commentService = new CommentService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/comment/delete".equals(request.getServletPath())) {
            handleDelete(request, response);
            return;
        }

        Long postId = RequestUtil.getLong(request, "postId", null);
        Long parentCommentId = RequestUtil.getLong(request, "parentCommentId", null);
        String content = request.getParameter("content");
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);

        if (postId == null) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글 정보를 확인할 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/board/list");
            return;
        }

        if (content == null || content.isBlank()) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "댓글 내용을 입력해 주세요.");
            response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
            return;
        }

        if (loginUser != null && loginUser.getUserId() != null) {
            if (commentService.addComment(postId, loginUser.getUserId(), parentCommentId, content.trim()) != null) {
                var post = boardService.getPostDetail(postId);
                if (post != null) {
                    SocialService.notifyComment(loginUser, post.getUserId(), post.getTitle(), postId);
                }

                request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "댓글이 등록되었습니다.");
                response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
                return;
            }
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "댓글 등록에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = RequestUtil.getLong(request, "postId", null);
        Long commentId = RequestUtil.getLong(request, "commentId", null);
        UserDTO loginUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);

        if (postId == null || commentId == null) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "댓글 정보를 확인할 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/board/list");
            return;
        }

        boolean deleted = commentService.deleteComment(commentId, loginUser);
        request.getSession().setAttribute(
                MessageKey.FLASH_MESSAGE,
                deleted ? "댓글이 삭제되었습니다." : "댓글을 삭제할 수 없습니다."
        );
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }
}
