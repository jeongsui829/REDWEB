package com.redweb.admin.controller;

import com.redweb.admin.service.AdminManagementService;
import com.redweb.board.dto.BoardDTO;
import com.redweb.board.dto.PostDTO;
import com.redweb.common.constant.SessionKey;
import com.redweb.common.util.ViewUtil;
import com.redweb.file.dto.AttachmentDTO;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final String LOCAL_POSTS_KEY = "localPosts";
    private static final String FLASH_MESSAGE_KEY = "adminFlashMessage";

    private final AdminManagementService managementService = new AdminManagementService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();

        if ("/admin/users".equals(servletPath)) {
            request.setAttribute("users", managementService.getUsers());
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/users");
            return;
        }

        if ("/admin/posts".equals(servletPath)) {
            request.setAttribute("posts", buildAdminPosts());
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/posts");
            return;
        }

        if ("/admin/reports".equals(servletPath)) {
            request.setAttribute("reports", managementService.getReports());
            request.setAttribute("flashMessage", consumeFlashMessage(request.getSession(false)));
            ViewUtil.forward(request, response, "admin/reports");
            return;
        }

        if ("/admin/notices".equals(servletPath)) {
            request.setAttribute("boards", managementService.getBoards());
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
        if (targetUser == null || !("ACTIVE".equals(status) || "SUSPENDED".equals(status))) {
            setFlashMessage(request.getSession(), "회원 상태 변경에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        if (status.equals(targetUser.getStatus())) {
            setFlashMessage(request.getSession(),
                    "ACTIVE".equals(status) ? "이미 활성화 상태입니다." : "이미 정지 상태입니다.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        boolean success = managementService.updateUserStatus(userId, status);
        setFlashMessage(request.getSession(), success ? "회원 상태를 변경했습니다." : "회원 상태 변경에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private void handleHidePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = parseLong(request.getParameter("postId"));
        boolean success = false;

        if (postId != null) {
            success = managementService.hidePost(postId);
            if (!success) {
                success = hideLocalPost(postId);
            }
        }

        setFlashMessage(request.getSession(), success ? "게시글을 숨김 처리했습니다." : "게시글 숨김 처리에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/posts");
    }

    private void handleDeletePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = parseLong(request.getParameter("postId"));
        boolean success = false;

        if (postId != null) {
            success = managementService.deletePost(postId);
            if (!success) {
                success = deleteLocalPost(postId);
            }
        }

        setFlashMessage(request.getSession(), success ? "게시글을 삭제했습니다." : "게시글 삭제에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/posts");
    }

    private void handleRestorePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = parseLong(request.getParameter("postId"));
        boolean success = false;

        if (postId != null) {
            success = managementService.restorePost(postId);
            if (!success) {
                success = restoreLocalPost(postId);
            }
        }

        setFlashMessage(request.getSession(), success ? "게시글을 다시 공개했습니다." : "게시글 숨김 해제에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/posts");
    }

    private void handleProcessReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long reportId = parseLong(request.getParameter("reportId"));
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        boolean success = managementService.resolveReport(reportId, adminUser);
        setFlashMessage(request.getSession(), success ? "신고를 처리 완료로 변경했습니다." : "이미 처리된 신고이거나 대상이 없습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/reports");
    }

    private void handleDismissReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long reportId = parseLong(request.getParameter("reportId"));
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);
        boolean success = managementService.dismissReport(reportId, adminUser);
        setFlashMessage(request.getSession(), success ? "신고를 조치 없음으로 정리했습니다." : "이미 처리된 신고이거나 대상이 없습니다.");
        response.sendRedirect(request.getContextPath() + "/admin/reports");
    }

    private void handleCreateNotice(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String boardCode = safeTrim(request.getParameter("boardCode"));
        String title = safeTrim(request.getParameter("title"));
        String content = safeTrim(request.getParameter("content"));
        UserDTO adminUser = (UserDTO) request.getSession().getAttribute(SessionKey.LOGIN_USER);

        if (boardCode.isEmpty() || title.isEmpty() || content.isEmpty() || adminUser == null) {
            setFlashMessage(request.getSession(), "공지 카테고리와 제목, 내용을 모두 입력해 주세요.");
            response.sendRedirect(request.getContextPath() + "/admin/notices");
            return;
        }

        Long createdId = managementService.createNotice(boardCode, title, content, adminUser.getUserId());
        if (createdId == null) {
            createLocalNotice(boardCode, title, content, adminUser);
        }

        setFlashMessage(request.getSession(), "공지 게시글을 등록했습니다.");
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
        Map<Long, PostDTO> merged = new LinkedHashMap<>();
        for (PostDTO post : managementService.getPosts()) {
            merged.put(post.getPostId(), post);
        }
        for (List<PostDTO> localPosts : getLocalPosts().values()) {
            for (PostDTO post : localPosts) {
                merged.put(post.getPostId(), post);
            }
        }

        List<PostDTO> posts = new ArrayList<>(merged.values());
        posts.sort(Comparator.comparing(PostDTO::getPostId, Comparator.nullsLast(Long::compareTo)).reversed());
        return posts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<PostDTO>> getLocalPosts() {
        Object value = getServletContext().getAttribute(LOCAL_POSTS_KEY);
        if (value instanceof Map<?, ?>) {
            return (Map<String, List<PostDTO>>) value;
        }
        Map<String, List<PostDTO>> posts = new LinkedHashMap<>();
        getServletContext().setAttribute(LOCAL_POSTS_KEY, posts);
        return posts;
    }

    private boolean hideLocalPost(Long postId) {
        for (List<PostDTO> posts : getLocalPosts().values()) {
            for (PostDTO post : posts) {
                if (postId.equals(post.getPostId())) {
                    post.setStatus("HIDDEN");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteLocalPost(Long postId) {
        for (List<PostDTO> posts : getLocalPosts().values()) {
            PostDTO target = null;
            for (PostDTO post : posts) {
                if (postId.equals(post.getPostId())) {
                    target = post;
                    break;
                }
            }

            if (target != null) {
                deleteAttachmentFiles(target.getAttachments());
                return posts.remove(target);
            }
        }
        return false;
    }

    private boolean restoreLocalPost(Long postId) {
        for (List<PostDTO> posts : getLocalPosts().values()) {
            for (PostDTO post : posts) {
                if (postId.equals(post.getPostId())) {
                    post.setStatus("PUBLIC");
                    return true;
                }
            }
        }
        return false;
    }

    private void createLocalNotice(String boardCode, String title, String content, UserDTO adminUser) {
        Map<String, List<PostDTO>> localPosts = getLocalPosts();
        List<PostDTO> boardPosts = localPosts.computeIfAbsent(boardCode, key -> new ArrayList<>());
        BoardDTO board = managementService.getBoards().stream()
                .filter(item -> boardCode.equals(item.getBoardCode()))
                .findFirst()
                .orElse(null);

        long nextId = localPosts.values().stream()
                .flatMap(List::stream)
                .map(PostDTO::getPostId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(900000L) + 1L;

        PostDTO notice = new PostDTO();
        notice.setPostId(nextId);
        notice.setUserId(adminUser.getUserId());
        notice.setAuthorNickname(adminUser.getNickname());
        notice.setBoardName(board != null ? board.getBoardName() : boardCode);
        notice.setCategoryName("운영 공지");
        notice.setTitle(title);
        notice.setContent(content);
        notice.setStatus("PUBLIC");
        notice.setNotice(true);
        notice.setPinned(true);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setAttachments(new ArrayList<>());
        boardPosts.add(0, notice);
    }

    private void deleteAttachmentFiles(List<AttachmentDTO> attachments) {
        if (attachments == null) {
            return;
        }
        for (AttachmentDTO attachment : attachments) {
            if (attachment == null || attachment.getFilePath() == null) {
                continue;
            }
            try {
                Files.deleteIfExists(Path.of(attachment.getFilePath()));
            } catch (Exception ignored) {
            }
        }
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
