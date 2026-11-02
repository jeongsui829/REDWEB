package com.scuredweb.admin.controller;

import com.scuredweb.admin.service.AdminManagementService;
import com.scuredweb.comment.dao.CommentDAO;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.util.RequestUtil;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

/** AdminFilter enforces current ACTIVE role and fresh step-up; CsrfFilter covers POST. */
@WebServlet("/admin/comments/delete")
public class AdminCommentController extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserDTO admin = (UserDTO) request.getSession().getAttribute(SessionKey.ADMIN_USER);
        Long id = RequestUtil.getLong(request, "commentId", null);
        if (id == null) { response.sendError(400); return; }
        try {
            if (!new CommentDAO().deleteCommentAudited(id, admin.getUserId())) { response.sendError(404); return; }
            boolean logged = true; // DAO committed moderation and audit together.
            request.getSession().setAttribute("flashMessage", logged ? "댓글이 삭제되었습니다." : "댓글이 삭제되었지만 감사 로그 저장에 실패했습니다.");
            Long postId = RequestUtil.getLong(request, "postId", null);
            response.sendRedirect(request.getContextPath() + (postId == null ? "/admin/posts" : "/board/detail?id=" + postId));
        } catch (java.sql.SQLException exception) { response.sendError(503); }
    }
}
