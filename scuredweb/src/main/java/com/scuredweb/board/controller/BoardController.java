package com.scuredweb.board.controller;

import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.board.service.BoardService;
import com.scuredweb.comment.dto.CommentDTO;
import com.scuredweb.comment.service.CommentService;
import com.scuredweb.common.constant.MessageKey;
import com.scuredweb.common.constant.SessionKey;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.FileValidationUtil;
import com.scuredweb.common.util.HtmlUtil;
import com.scuredweb.common.util.InputValidator;
import com.scuredweb.common.util.RequestUtil;
import com.scuredweb.common.util.UploadPathUtil;
import com.scuredweb.common.util.StoredFileCleanup;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.file.dto.AttachmentDTO;
import com.scuredweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = FileValidationUtil.MAX_ATTACHMENT_SIZE,
        maxRequestSize = FileValidationUtil.MAX_ATTACHMENT_SIZE * FileValidationUtil.MAX_ATTACHMENTS_PER_REQUEST
)
@WebServlet(urlPatterns = {
        "/board/list",
        "/board/detail",
        "/board/write",
        "/board/edit",
        "/board/delete",
        "/board/recommend",
        "/board/dislike"
})
public class BoardController extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(BoardController.class.getName());
    private static final String VIEW_THROTTLE_CONTEXT_KEY = "viewThrottle";
    private static final int MAX_VIEW_THROTTLE_ENTRIES = 10_000;
    private static final long VIEW_COUNT_INTERVAL_MILLIS = 30L * 60L * 1000L;
    private static final long VIEW_THROTTLE_CLEANUP_INTERVAL_MILLIS = 60L * 1000L;
    private static volatile long lastViewThrottleCleanupAt;
    private final BoardService boardService = new BoardService();
    private final CommentService commentService = new CommentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();
        switch (servletPath) {
            case "/board/list" -> handleList(request, response);
            case "/board/detail" -> handleDetail(request, response);
            case "/board/write" -> {
                if (!requireLogin(request, response)) {
                    return;
                }
                try {
                    request.setAttribute("boards", boardService.getBoards());
                } catch (DataAccessException exception) {
                    request.setAttribute("boards", List.of());
                    request.setAttribute("boardsLoadError", true);
                }
                try {
                    request.setAttribute("popularTags", boardService.getPopularTags());
                } catch (DataAccessException exception) {
                    request.setAttribute("popularTags", List.of());
                    request.setAttribute("popularTagsLoadError", true);
                }
                HttpSession session = request.getSession(false);
                request.setAttribute("flashMessage", session == null ? null : session.getAttribute(MessageKey.FLASH_MESSAGE));
                request.setAttribute("errorMessage", session == null ? null : session.getAttribute(MessageKey.ERROR_MESSAGE));
                if (session != null) {
                    session.removeAttribute(MessageKey.FLASH_MESSAGE);
                    session.removeAttribute(MessageKey.ERROR_MESSAGE);
                }
                ViewUtil.forward(request, response, "board/write");
            }
            case "/board/edit" -> handleEditForm(request, response);
            default -> response.sendRedirect(request.getContextPath() + "/home");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        switch (request.getServletPath()) {
            case "/board/write" -> handleWrite(request, response);
            case "/board/edit" -> handleEdit(request, response);
            case "/board/delete" -> handleDelete(request, response);
            case "/board/recommend" -> handleReaction(request, response, "LIKE");
            case "/board/dislike" -> handleReaction(request, response, "DISLIKE");
            default -> response.sendRedirect(request.getContextPath() + "/home");
        }
    }

    private void handleList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String requestedBoardCode = request.getParameter("boardCode");
        if (requestedBoardCode != null && !InputValidator.isBoardCode(requestedBoardCode)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String boardCode = requestedBoardCode == null ? "free" : requestedBoardCode.trim();
        String keyword = InputValidator.limit(request.getParameter("keyword"), 100);
        String sort = InputValidator.sortOrDefault(request.getParameter("sort"), "latest");
        List<PostDTO> posts = new ArrayList<>();
        try {
            posts.addAll(keyword != null && !keyword.isBlank()
                    ? boardService.searchBoardPosts(boardCode, keyword)
                    : boardService.getBoardPosts(boardCode));
        } catch (DataAccessException exception) {
            request.setAttribute("postsLoadError", true);
        }
        sortPosts(posts, sort);

        try {
            var currentBoard = boardService.getBoardByCode(boardCode);
            if (currentBoard == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("currentBoard", currentBoard);
        } catch (DataAccessException exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        try {
            request.setAttribute("popularPosts", boardService.getTrendingPosts());
        } catch (DataAccessException exception) {
            request.setAttribute("popularPosts", List.of());
            request.setAttribute("popularPostsLoadError", true);
        }
        try {
            request.setAttribute("boards", boardService.getBoards());
        } catch (DataAccessException exception) {
            request.setAttribute("boards", List.of());
            request.setAttribute("boardsLoadError", true);
        }
        try {
            request.setAttribute("popularTags", boardService.getPopularTags());
        } catch (DataAccessException exception) {
            request.setAttribute("popularTags", List.of());
            request.setAttribute("popularTagsLoadError", true);
        }

        HttpSession session = request.getSession(false);
        request.setAttribute("boardCode", boardCode);
        request.setAttribute("keyword", keyword != null ? keyword.trim() : "");
        request.setAttribute("sort", sort);
        request.setAttribute("posts", posts);
        request.setAttribute("flashMessage", session == null ? null : session.getAttribute(MessageKey.FLASH_MESSAGE));
        if (session != null) {
            session.removeAttribute(MessageKey.FLASH_MESSAGE);
        }
        ViewUtil.forward(request, response, "board/list");
    }

    private void handleDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Long postId = RequestUtil.getLong(request, "id", null);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        incrementViewCountWithThrottle(request, postId);
        PostDTO post;
        try {
            post = boardService.getPostDetail(postId);
        } catch (DataAccessException exception) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        if (post == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        List<CommentDTO> comments = new ArrayList<>(commentService.getComments(postId));
        int activeCommentCount = 0;
        for (CommentDTO comment : comments) {
            if (!comment.isDeleted()) activeCommentCount++;
            for (CommentDTO reply : comment.getDescendants()) {
                if (!reply.isDeleted()) activeCommentCount++;
            }
        }
        post.setCommentCount(activeCommentCount);

        Long loginUserId = resolveLoginUserId(request.getSession(false));
        String reaction = loginUserId == null ? null : boardService.getReactionType(postId, loginUserId);
        HttpSession session = request.getSession(false);
        request.setAttribute("post", post);
        request.setAttribute("comments", comments);
        request.setAttribute("recommended", "LIKE".equals(reaction));
        request.setAttribute("disliked", "DISLIKE".equals(reaction));
        request.setAttribute("canManagePost", canManagePost(request, post));
        request.setAttribute("flashMessage", session == null ? null : session.getAttribute(MessageKey.FLASH_MESSAGE));
        request.setAttribute("errorMessage", session == null ? null : session.getAttribute(MessageKey.ERROR_MESSAGE));
        if (session != null) {
            session.removeAttribute(MessageKey.FLASH_MESSAGE);
            session.removeAttribute(MessageKey.ERROR_MESSAGE);
        }
        ViewUtil.forward(request, response, "board/detail");
    }

    private void handleEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        Long postId = RequestUtil.getLong(request, "id", null);
        PostDTO post = boardService.getPostDetail(postId);
        if (post == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!canManagePost(request, post)) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "본인이 작성한 게시글만 수정할 수 있습니다.");
            response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
            return;
        }
        request.setAttribute("post", post);
        request.setAttribute("boards", boardService.getBoards());
        ViewUtil.forward(request, response, "board/edit");
    }

    private void handleWrite(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!requireLogin(request, response)) {
            return;
        }
        String boardCode = InputValidator.trimmed(request.getParameter("boardCode"));
        String title = InputValidator.trimmed(request.getParameter("title"));
        String content = InputValidator.trimmed(request.getParameter("content"));
        if (!InputValidator.isBoardCode(boardCode)
                || !InputValidator.hasLength(title, 1, 200)
                || !InputValidator.hasLength(content, 1, 20000)) {
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE,
                    "게시판, 제목(최대 200자), 내용을 확인하세요.");
            response.sendRedirect(request.getContextPath() + "/board/write");
            return;
        }

        validateUploadPartCount(request, Set.of("attachments", "contentImages"));
        List<AttachmentDTO> uploads = new ArrayList<>();
        boolean committed = false;
        try {
            List<AttachmentDTO> attachments = saveUploadedAttachments(request, "attachments", uploads);
            List<AttachmentDTO> contentImages = saveUploadedAttachments(request, "contentImages", uploads);
            String resolvedContent = appendInlineImages(request, content.trim(), contentImages);
            Long writerUserId = resolveLoginUserId(request.getSession(false));
            Long createdPostId = writerUserId == null ? null : boardService.createPost(boardCode, title.trim(), resolvedContent, writerUserId);
            if (createdPostId != null) {
                if (!uploads.isEmpty() && !boardService.saveAttachments(createdPostId, writerUserId, uploads)) {
                    deleteUploadedFiles(uploads);
                    boolean compensated = boardService.deletePost(createdPostId, writerUserId);
                    if (!compensated) {
                        LOGGER.severe("postId=" + createdPostId + ", stage=create-compensation-delete");
                    } else {
                        LOGGER.warning("postId=" + createdPostId + ", stage=create-attachment-save-compensated");
                    }
                    request.getSession().setAttribute(MessageKey.ERROR_MESSAGE,
                            compensated
                                    ? "첨부파일 저장에 실패해 게시글 등록을 취소했습니다."
                                    : "첨부파일 저장에 실패했고 게시글 정리도 완료되지 않았습니다. 관리자 확인이 필요합니다.");
                    response.sendRedirect(request.getContextPath() + "/board/write?boardCode=" + boardCode);
                    return;
                }
                committed = true;
                request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글이 등록되었습니다.");
                response.sendRedirect(request.getContextPath() + "/board/detail?id=" + createdPostId);
                return;
            }
        } catch (DataAccessException exception) {
            deleteUploadedFiles(uploads);
            request.getSession().setAttribute(MessageKey.ERROR_MESSAGE, "게시글 등록에 실패했습니다.");
            response.sendRedirect(request.getContextPath() + "/board/write");
            return;
        } catch (IOException | ServletException | RuntimeException exception) {
            if (!committed) deleteUploadedFiles(uploads);
            LOGGER.log(Level.WARNING, "Post write failed while processing upload.", exception);
            throw exception;
        }
        deleteUploadedFiles(uploads);
        LOGGER.warning("Post write failed. boardCode=" + boardCode + ", writerUserId=" + resolveLoginUserId(request.getSession(false)));
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글 등록에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/board/write");
    }

    private void handleEdit(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!requireLogin(request, response)) {
            return;
        }
        Long postId = RequestUtil.getLong(request, "id", null);
        String title = InputValidator.trimmed(request.getParameter("title"));
        String content = InputValidator.trimmed(request.getParameter("content"));
        if (postId == null
                || !InputValidator.hasLength(title, 1, 200)
                || !InputValidator.hasLength(content, 1, 20000)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        validateUploadPartCount(request, Set.of("attachments", "contentImages"));
        Long loginUserId = resolveLoginUserId(request.getSession(false));
        PostDTO originalPost = boardService.getPostDetail(postId);
        if (originalPost == null || !loginUserId.equals(originalPost.getUserId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        List<AttachmentDTO> uploads = new ArrayList<>();
        boolean committed = false;
        try {
            List<AttachmentDTO> attachments = saveUploadedAttachments(request, "attachments", uploads);
            List<AttachmentDTO> contentImages = saveUploadedAttachments(request, "contentImages", uploads);
            String resolvedContent = appendInlineImages(request, content.trim(), contentImages);
            if (loginUserId != null && boardService.updatePost(postId, loginUserId, title.trim(), resolvedContent)) {
                if (!uploads.isEmpty() && !boardService.saveAttachments(postId, loginUserId, uploads)) {
                    deleteUploadedFiles(uploads);
                    boolean reverted = originalPost != null && boardService.updatePost(
                            postId, loginUserId, originalPost.getTitle(), originalPost.getContent());
                    if (!reverted) {
                        LOGGER.severe("postId=" + postId + ", stage=edit-compensation-update");
                    } else {
                        LOGGER.warning("postId=" + postId + ", stage=edit-attachment-save-compensated");
                    }
                    request.getSession().setAttribute(MessageKey.ERROR_MESSAGE,
                            reverted
                                    ? "첨부파일 저장에 실패해 게시글 수정을 되돌렸습니다."
                                    : "첨부파일 저장에 실패했고 게시글 수정 복구도 완료되지 않았습니다. 관리자 확인이 필요합니다.");
                    response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
                    return;
                }
                committed = true;
                request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글이 수정되었습니다.");
                response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
                return;
            }
        } catch (IOException | ServletException | RuntimeException exception) {
            if (!committed) deleteUploadedFiles(uploads);
            throw exception;
        }
        deleteUploadedFiles(uploads);
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "본인이 작성한 게시글만 수정할 수 있습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = RequestUtil.getLong(request, "id", null);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Long loginUserId = resolveLoginUserId(request.getSession(false));
        boolean deleted = loginUserId != null && boardService.deletePost(postId, loginUserId);
        if (deleted) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글이 삭제되었습니다.");
            response.sendRedirect(request.getContextPath() + "/board/list?boardCode=free");
            return;
        }
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글 삭제 권한이 없습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private void handleReaction(HttpServletRequest request, HttpServletResponse response, String reactionType) throws IOException {
        if (!requireLogin(request, response)) {
            return;
        }
        Long postId = RequestUtil.getLong(request, "id", null);
        Long userId = resolveLoginUserId(request.getSession(false));
        if (postId == null || userId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        boolean reacted = boardService.reactToPost(postId, userId, reactionType);
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE,
                reacted ? ("LIKE".equals(reactionType) ? "게시글을 추천했습니다." : "게시글을 비추천했습니다.") : "반응 처리에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private List<AttachmentDTO> saveUploadedAttachments(HttpServletRequest request, String fieldName, List<AttachmentDTO> cleanup) throws IOException, ServletException {
        List<AttachmentDTO> attachments = new ArrayList<>();
        Path uploadDirectory = UploadPathUtil.getAttachmentsDirectory();
        int fileCount = 0;
        for (Part part : request.getParts()) {
            if (!fieldName.equals(part.getName()) || part.getSize() <= 0) {
                continue;
            }
            fileCount++;
            if (fileCount > FileValidationUtil.MAX_ATTACHMENTS_PER_REQUEST || !FileValidationUtil.isAllowedAttachment(part)) {
                throw new ServletException("Invalid attachment upload.");
            }
            String originalName = FileValidationUtil.cleanOriginalName(part.getSubmittedFileName());
            String extension = FileValidationUtil.extension(originalName);
            String savedName = UUID.randomUUID() + extension;
            Path target = FileValidationUtil.safeResolve(uploadDirectory, savedName);
            AttachmentDTO attachment = new AttachmentDTO();
            attachment.setOriginalName(originalName);
            attachment.setSavedName(savedName);
            attachment.setFilePath(target.toString());
            attachment.setFileSize(part.getSize());
            attachment.setImage(FileValidationUtil.IMAGE_EXTENSIONS.contains(extension));
            attachment.setFileExt(extension);
            attachment.setMimeType(FileValidationUtil.safeContentType(extension));
            cleanup.add(attachment);
            com.scuredweb.common.security.UploadBudget.checkSpace(uploadDirectory, part.getSize());
            try (var inputStream = part.getInputStream()) {
                Files.copy(inputStream, target);
            }
            attachments.add(attachment);
        }
        return attachments;
    }

    private void validateUploadPartCount(HttpServletRequest request, Set<String> fieldNames) throws IOException, ServletException {
        long totalBytes = 0;
        int fileCount = 0;
        for (Part part : request.getParts()) {
            if (fieldNames.contains(part.getName()) && part.getSize() > 0) {
                fileCount++;
                totalBytes = Math.addExact(totalBytes, part.getSize());
            }
        }
        if (totalBytes > 0) {
            try (var connection = com.scuredweb.common.util.DBUtil.getConnection()) {
                com.scuredweb.common.security.UploadBudget.checkQuota(connection, resolveLoginUserId(request.getSession(false)), totalBytes, false);
                com.scuredweb.common.security.UploadBudget.checkSpace(UploadPathUtil.getAttachmentsDirectory(), totalBytes);
            } catch (java.sql.SQLException exception) { throw new IOException("Cannot verify upload quota", exception); }
        }
        if (fileCount > FileValidationUtil.MAX_ATTACHMENTS_PER_REQUEST) {
            throw new ServletException("Too many uploaded files.");
        }
    }

    private String appendInlineImages(HttpServletRequest request, String content, List<AttachmentDTO> contentImages) {
        if (contentImages == null || contentImages.isEmpty()) {
            return content;
        }
        StringBuilder builder = new StringBuilder(content);
        for (AttachmentDTO image : contentImages) {
            String marker = "[[image:" + image.getOriginalName() + "]]";
            String imageTag = buildInlineImageTag(request, image);
            int markerIndex = builder.indexOf(marker);
            if (markerIndex >= 0) {
                while (markerIndex >= 0) {
                    builder.replace(markerIndex, markerIndex + marker.length(), imageTag);
                    markerIndex = builder.indexOf(marker, markerIndex + imageTag.length());
                }
            } else {
                builder.append("\n").append(imageTag);
            }
        }
        return builder.toString();
    }

    private String buildInlineImageTag(HttpServletRequest request, AttachmentDTO image) {
        return "<img src=\"" + HtmlUtil.escapeAttribute(request.getContextPath()) + "/files/image?savedName="
                + HtmlUtil.escapeUrlParam(image.getSavedName())
                + "\" alt=\""
                + HtmlUtil.escapeAttribute(image.getOriginalName())
                + "\">";
    }

    private void deleteUploadedFiles(List<AttachmentDTO> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            return;
        }
        StoredFileCleanup.deleteAttachments(
                uploads.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(AttachmentDTO::getSavedName)
                        .toList(),
                "upload-compensation");
    }

    private boolean requireLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (resolveLoginUserId(request.getSession(false)) != null) {
            return true;
        }
        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "로그인이 필요합니다.");
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }

    private Long resolveLoginUserId(HttpSession session) {
        if (session == null) {
            return null;
        }
        UserDTO loginUser = (UserDTO) session.getAttribute(SessionKey.LOGIN_USER);
        return loginUser == null ? null : loginUser.getUserId();
    }

    private boolean canManagePost(HttpServletRequest request, PostDTO post) {
        if (post == null) {
            return false;
        }
        Long loginUserId = resolveLoginUserId(request.getSession(false));
        return loginUserId != null && post.getUserId() != null && loginUserId.equals(post.getUserId());
    }


    private void incrementViewCountWithThrottle(HttpServletRequest request, Long postId) {
        if (postId == null) {
            return;
        }
        ConcurrentHashMap<String, Long> throttle = getViewThrottleMap(request);
        String key = buildViewThrottleKey(request, postId);
        long now = System.currentTimeMillis();
        Long lastViewedAt = throttle.get(key);
        if (lastViewedAt == null || now - lastViewedAt > VIEW_COUNT_INTERVAL_MILLIS) {
            boardService.incrementViewCount(postId);
            throttle.put(key, now);
        }
        cleanupViewThrottleIfNeeded(throttle, now);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Long> getViewThrottleMap(HttpServletRequest request) {
        Object stored = request.getServletContext().getAttribute(VIEW_THROTTLE_CONTEXT_KEY);
        if (stored instanceof ConcurrentHashMap<?, ?>) {
            return (ConcurrentHashMap<String, Long>) stored;
        }
        ConcurrentHashMap<String, Long> throttle = new ConcurrentHashMap<>();
        request.getServletContext().setAttribute(VIEW_THROTTLE_CONTEXT_KEY, throttle);
        return throttle;
    }

    private String buildViewThrottleKey(HttpServletRequest request, Long postId) {
        // Local in-memory throttle for a single WAS. Use DB/Redis for clustered production deployments.
        String remoteAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String source = postId + "|" + remoteAddress + "|" + String.valueOf(userAgent);
        return sha256(source);
    }

    private void cleanupViewThrottleIfNeeded(ConcurrentHashMap<String, Long> throttle, long now) {
        if (now - lastViewThrottleCleanupAt < VIEW_THROTTLE_CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastViewThrottleCleanupAt = now;
        throttle.entrySet().removeIf(entry -> now - entry.getValue() > VIEW_COUNT_INTERVAL_MILLIS);
        int overflow = throttle.size() - MAX_VIEW_THROTTLE_ENTRIES;
        if (overflow <= 0) {
            return;
        }
        throttle.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(overflow)
                .map(Map.Entry::getKey)
                .forEach(throttle::remove);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("View throttle hashing is not available", exception);
        }
    }

    private void sortPosts(List<PostDTO> posts, String sort) {
        Comparator<PostDTO> comparator = switch (sort) {
            case "views" -> Comparator.comparingInt(PostDTO::getViewCount).reversed()
                    .thenComparing(PostDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "likes" -> Comparator.comparingInt(PostDTO::getLikeCount).reversed()
                    .thenComparing(Comparator.comparingInt(PostDTO::getViewCount).reversed())
                    .thenComparing(PostDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(PostDTO::isPinned).reversed()
                    .thenComparing(PostDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PostDTO::getPostId, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        posts.sort(comparator);
    }
}

