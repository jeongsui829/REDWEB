package com.scuredweb.admin.controller;

import com.scuredweb.admin.service.AdminManagementService;
import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@WebServlet(urlPatterns = {
        "/admin/users",
        "/admin/users/status",
        "/admin/posts",
        "/admin/posts/hide",
        "/admin/posts/restore",
        "/admin/posts/delete",
        "/admin/reports",
        "/admin/reports/process",
        "/admin/reports/dismiss",
        "/admin/notices",
        "/admin/notices/create"
})
public class AdminManagementController extends HttpServlet {
    private static final String FLASH_MESSAGE_KEY = "adminFlashMessage";

    private final AdminManagementService managementService = new AdminManagementService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();

        if ("/admin/users".equals(servletPath)) {
            try {
                request.setAttribute("users", managementService.getUsers());
            } catch (DataAccessException exception) {
                request.setAttribute("users", List.of());
                request.setAttribute("usersLoadError", true);
            }
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/users");
            return;
        }

        if ("/admin/posts".equals(servletPath)) {
            try {
                request.setAttribute("posts", buildAdminPosts());
            } catch (DataAccessException exception) {
                request.setAttribute("posts", List.of());
                request.setAttribute("postsLoadError", true);
            }
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/posts");
            return;
        }

        if ("/admin/reports".equals(servletPath)) {
            try {
                request.setAttribute("reports", managementService.getReports());
            } catch (DataAccessException exception) {
                request.setAttribute("reports", List.of());
                request.setAttribute("reportsLoadError", true);
            }
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/reports");
            return;
        }

        if ("/admin/notices".equals(servletPath)) {
            try {
                request.setAttribute("boards", managementService.getBoards());
            } catch (DataAccessException exception) {
                request.setAttribute("boards", List.of());
                request.setAttribute("boardsLoadError", true);
            }
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/notices");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/admin/dashboard");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();

        if ("/admin/users/status".equals(servletPath)) {
            handleUserStatus(request, response);
            return;
        }

        if ("/admin/posts/hide".equals(servletPath)) {
            handleHidePost(request, response);
            return;
        }

        if ("/admin/posts/restore".equals(servletPath)) {
            handleRestorePost(request, response);
            return;
        }

        if ("/admin/posts/delete".equals(servletPath)) {
            handleDeletePost(request, response);
            return;
        }

        if ("/admin/reports/process".equals(servletPath)) {
            handleProcessReport(request, response);
            return;
        }

        if ("/admin/reports/dismiss".equals(servletPath)) {
            handleDismissReport(request, response);
            return;
        }

        if ("/admin/notices/create".equals(servletPath)) {
            handleCreateNotice(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/admin/dashboard");
    }

    private void handleUserStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long userId = parseLong(request.getParameter("userId"));
        String status = safeTrim(request.getParameter("status"));

        UserDTO targetUser = findUser(userId);
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        if (targetUser == null || !("ACTIVE".equals(status) || "SUSPENDED".equals(status))) {
            setFlashMessage(request.getSession(), "회원 상태 변경에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        if ("SUSPENDED".equals(status) && adminUser != null && userId.equals(adminUser.getUserId())) {
            setFlashMessage(request.getSession(), "현재 로그인한 관리자 계정은 직접 정지할 수 없습니다.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        if (status.equals(targetUser.getStatus())) {
            setFlashMessage(request.getSession(),
                    "ACTIVE".equals(status) ? "이미 활성 상태입니다." : "이미 정지 상태입니다.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        AdminManagementService.ActionResult result = managementService.updateUserStatus(userId, status, adminUser);
        setFlashMessage(request.getSession(), actionMessage(result,
                "회원 상태를 변경했습니다.",
                "회원 상태 변경에 실패했습니다. 자기 계정 또는 마지막 활성 관리자 계정은 정지할 수 없습니다."));
        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private void handleHidePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = parseLong(request.getParameter("postId"));
        AdminManagementService.ActionResult result = AdminManagementService.ActionResult.failed();

        if (postId != null) {
            result = managementService.hidePost(postId, currentAdmin(request));
        }

        setFlashMessage(request.getSession(), actionMessage(result,
                "게시글을 숨김 처리했습니다.", "게시글 숨김 처리에 실패했습니다."));
        response.sendRedirect(request.getContextPath() + "/admin/posts");
    }

    private void handleDeletePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = parseLong(request.getParameter("postId"));
        AdminManagementService.ActionResult result = AdminManagementService.ActionResult.failed();

        if (postId != null) {
            result = managementService.deletePost(postId, currentAdmin(request));
        }

        setFlashMessage(request.getSession(), actionMessage(result,
                "게시글을 삭제했습니다.", "게시글 삭제에 실패했습니다."));
        response.sendRedirect(request.getContextPath() + "/admin/posts");
    }

    private void handleRestorePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = parseLong(request.getParameter("postId"));
        AdminManagementService.ActionResult result = AdminManagementService.ActionResult.failed();

        if (postId != null) {
            result = managementService.restorePost(postId, currentAdmin(request));
        }

        setFlashMessage(request.getSession(), actionMessage(result,
                "게시글을 다시 공개했습니다.", "게시글 복구에 실패했습니다."));
        response.sendRedirect(request.getContextPath() + "/admin/posts");
    }

    private void handleProcessReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long reportId = parseLong(request.getParameter("reportId"));
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        boolean success = managementService.resolveReport(reportId, adminUser);
        boolean audited = success; // Same DB transaction in DAO.
        setFlashMessage(request.getSession(), success
                ? (audited ? "신고를 처리 완료로 변경했습니다." : partialAuditMessage())
                : "이미 처리된 신고이거나 대상이 없습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/reports");
    }

    private void handleDismissReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long reportId = parseLong(request.getParameter("reportId"));
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        boolean success = managementService.dismissReport(reportId, adminUser);
        boolean audited = success; // Same DB transaction in DAO.
        setFlashMessage(request.getSession(), success
                ? (audited ? "신고를 조치 없음으로 정리했습니다." : partialAuditMessage())
                : "이미 처리된 신고이거나 대상이 없습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/reports");
    }

    private void handleCreateNotice(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String boardCode = safeTrim(request.getParameter("boardCode"));
        String title = safeTrim(request.getParameter("title"));
        String content = safeTrim(request.getParameter("content"));
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);

        if (!InputValidator.isBoardCode(boardCode)
                || !InputValidator.hasLength(title, 1, 200)
                || content.isEmpty()
                || adminUser == null) {
            setFlashMessage(request.getSession(), "공지 게시판, 제목(최대 200자), 내용을 확인하세요.");
            response.sendRedirect(request.getContextPath() + "/admin/notices");
            return;
        }

        Long createdId;
        try {
            createdId = managementService.createNotice(boardCode, title, content, adminUser.getUserId());
        } catch (DataAccessException exception) {
            createdId = null;
        }

        boolean audited = createdId != null; // Same DB transaction in DAO.
        setFlashMessage(request.getSession(), createdId == null
                ? "공지 게시글 등록에 실패했습니다."
                : audited ? "공지 게시글이 등록되었습니다." : partialAuditMessage());
        response.sendRedirect(request.getContextPath() + "/admin/notices");
    }

    private UserDTO findUser(Long userId) {
        if (userId == null) {
            return null;
        }
        for (UserDTO user : managementService.getUsers()) {
            if (userId.equals(user.getUserId())) {
                return user;
            }
        }
        return null;
    }

    private List<PostDTO> buildAdminPosts() {
        List<PostDTO> posts = new ArrayList<>(managementService.getPosts());
        posts.sort(Comparator.comparing(PostDTO::getPostId, Comparator.nullsLast(Long::compareTo)).reversed());
        return posts;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private UserDTO currentAdmin(HttpServletRequest request) {
        return (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
    }

    private String actionMessage(AdminManagementService.ActionResult result, String success, String failure) {
        if (!result.completed()) {
            return failure;
        }
        return result.auditLogged() ? success : partialAuditMessage();
    }

    private String partialAuditMessage() {
        return "작업은 처리되었지만 감사 로그 기록에 실패했습니다. 관리자 확인이 필요합니다.";
    }

    private void setFlashMessage(HttpSession session, String message) {
        if (session != null) {
            session.setAttribute(FLASH_MESSAGE_KEY, message);
        }
    }

    private String consumeFlashMessage(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(FLASH_MESSAGE_KEY);
        session.removeAttribute(FLASH_MESSAGE_KEY);
        return value == null ? null : value.toString();
    }
}

