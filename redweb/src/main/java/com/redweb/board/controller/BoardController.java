package com.redweb.board.controller;

import com.redweb.board.dto.PostDTO;
import com.redweb.board.service.BoardService;
import com.redweb.comment.dto.CommentDTO;
import com.redweb.comment.service.CommentService;
import com.redweb.common.constant.MessageKey;
import com.redweb.common.constant.RoleCode;
import com.redweb.common.util.RequestUtil;
import com.redweb.common.util.UploadPathUtil;
import com.redweb.common.util.ViewUtil;
import com.redweb.file.dto.AttachmentDTO;
import com.redweb.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@MultipartConfig
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
    private final BoardService boardService = new BoardService();
    private final CommentService commentService = new CommentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();

        if ("/board/list".equals(servletPath)) {
            handleList(request, response);
            return;
        }

        if ("/board/detail".equals(servletPath)) {
            handleDetail(request, response);
            return;
        }

        if ("/board/write".equals(servletPath)) {
            request.setAttribute("boards", boardService.getBoards());
            request.setAttribute("popularTags", boardService.getPopularTags());
            ViewUtil.forward(request, response, "board/write");
            return;
        }

        if ("/board/edit".equals(servletPath)) {
            Long postId = RequestUtil.getLong(request, "id", null);
            PostDTO post = boardService.getPostDetail(postId);
            if (post == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            applyPostCounters(request, post);

            if (!canManagePost(request, post)) {
                request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "본인이 작성한 게시글만 수정할 수 있습니다.");
                response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
                return;
            }

            request.setAttribute("post", post);
            request.setAttribute("boards", boardService.getBoards());
            ViewUtil.forward(request, response, "board/edit");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/home");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String servletPath = request.getServletPath();

        switch (servletPath) {
            case "/board/write" -> handleWrite(request, response);
            case "/board/edit" -> handleEdit(request, response);
            case "/board/delete" -> handleDelete(request, response);
            case "/board/recommend" -> handleRecommend(request, response);
            case "/board/dislike" -> handleDislike(request, response);
            default -> response.sendRedirect(request.getContextPath() + "/home");
        }
    }

    private void handleList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String boardCode = request.getParameter("boardCode");
        String keyword = request.getParameter("keyword");
        String sort = request.getParameter("sort");
        if (boardCode == null || boardCode.isBlank()) {
            boardCode = "free";
        }
        if (sort == null || sort.isBlank()) {
            sort = "latest";
        }

        List<PostDTO> posts;
        if (keyword != null && !keyword.isBlank()) {
            posts = new ArrayList<>(boardService.searchBoardPosts(boardCode, keyword));
        } else {
            posts = new ArrayList<>(boardService.getBoardPosts(boardCode));
        }
        for (PostDTO post : posts) {
            applyPostCounters(request, post);
        }
        sortPosts(posts, sort);

        request.setAttribute("boardCode", boardCode);
        request.setAttribute("keyword", keyword != null ? keyword.trim() : "");
        request.setAttribute("sort", sort);
        request.setAttribute("currentBoard", boardService.getBoardByCode(boardCode));
        request.setAttribute("posts", posts);
        request.setAttribute("boards", boardService.getBoards());
        request.setAttribute("popularTags", boardService.getPopularTags());
        request.setAttribute("flashMessage", request.getSession().getAttribute(MessageKey.FLASH_MESSAGE));
        request.getSession().removeAttribute(MessageKey.FLASH_MESSAGE);
        ViewUtil.forward(request, response, "board/list");
    }

    private void handleDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String rawPostId = request.getParameter("id");
        if (rawPostId == null || rawPostId.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Long postId = parseLongOrNull(rawPostId);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        PostDTO post = boardService.getPostDetail(rawPostId);
        if (post == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        incrementViewCount(request, postId);
        applyPostCounters(request, post);

        List<CommentDTO> comments = new ArrayList<>(commentService.getComments(postId));
        if (post != null && !comments.isEmpty()) {
            post.setCommentCount(Math.max(post.getCommentCount(), comments.size()));
        }

        request.setAttribute("post", post);
        request.setAttribute("comments", comments);
        request.setAttribute("recommended", getLikedPosts(request).contains(postId));
        request.setAttribute("disliked", getDislikedPosts(request).contains(postId));
        request.setAttribute("canManagePost", canManagePost(request, post));
        request.setAttribute("flashMessage", request.getSession().getAttribute(MessageKey.FLASH_MESSAGE));
        request.getSession().removeAttribute(MessageKey.FLASH_MESSAGE);
        ViewUtil.forward(request, response, "board/detail");
    }

    private void handleWrite(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String boardCode = request.getParameter("boardCode");
        String title = request.getParameter("title");
        String content = request.getParameter("content");

        if (boardCode == null || boardCode.isBlank()) {
            boardCode = "free";
        }

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "제목과 내용을 입력해주세요.");
            response.sendRedirect(request.getContextPath() + "/board/write");
            return;
        }

        List<AttachmentDTO> attachments = saveUploadedAttachments(request, "attachments");
        List<AttachmentDTO> contentImages = saveUploadedAttachments(request, "contentImages");
        List<AttachmentDTO> allUploads = new ArrayList<>(attachments);
        allUploads.addAll(contentImages);
        String resolvedContent = appendInlineImages(request, content.trim(), contentImages);

        Long writerUserId = resolveLoginUserId(request.getSession());
        if (writerUserId == null) {
            writerUserId = 2L;
        }

        Long createdPostId = boardService.createPost(boardCode, title.trim(), resolvedContent, writerUserId);
        if (createdPostId != null) {
            if (!allUploads.isEmpty()) {
                boardService.saveAttachments(createdPostId, writerUserId, allUploads);
            }
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글이 등록되었습니다.");
            response.sendRedirect(request.getContextPath() + "/board/detail?id=" + createdPostId);
            return;
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글 등록에 실패했습니다.");
        response.sendRedirect(request.getContextPath() + "/board/write");
    }

    private void handleEdit(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Long postId = RequestUtil.getLong(request, "id", null);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String title = request.getParameter("title");
        String content = request.getParameter("content");
        List<AttachmentDTO> attachments = saveUploadedAttachments(request, "attachments");
        List<AttachmentDTO> contentImages = saveUploadedAttachments(request, "contentImages");
        List<AttachmentDTO> allUploads = new ArrayList<>(attachments);
        allUploads.addAll(contentImages);
        String resolvedContent = appendInlineImages(request, content == null ? "" : content.trim(), contentImages);

        Long loginUserId = resolveLoginUserId(request.getSession());
        if (loginUserId != null && boardService.updatePost(postId, loginUserId, title, resolvedContent)) {
            if (!allUploads.isEmpty()) {
                boardService.saveAttachments(postId, loginUserId, allUploads);
            }
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글이 수정되었습니다.");
            response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
            return;
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "본인이 작성한 게시글만 수정할 수 있습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = RequestUtil.getLong(request, "id", null);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        PostDTO existingPost = boardService.getPostDetail(postId);

        if (isAdmin(request.getSession())) {
            if (boardService.deletePostAsAdmin(postId)) {
                deletePhysicalAttachments(existingPost);
                clearReactions(request, postId);
                request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "관리자 권한으로 게시글을 삭제했습니다.");
                response.sendRedirect(request.getContextPath() + "/board/list?boardCode=free");
                return;
            }
        }

        Long loginUserId = resolveLoginUserId(request.getSession());
        if (loginUserId != null && boardService.deletePost(postId, loginUserId)) {
            deletePhysicalAttachments(existingPost);
            clearReactions(request, postId);
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글이 삭제되었습니다.");
            response.sendRedirect(request.getContextPath() + "/board/list?boardCode=free");
            return;
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "본인이 작성한 게시글만 삭제할 수 있습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private void handleRecommend(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = RequestUtil.getLong(request, "id", null);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Set<Long> likedPosts = getLikedPosts(request);
        Set<Long> dislikedPosts = getDislikedPosts(request);
        Map<Long, Integer> likeAdjustments = getLikeAdjustments(request);
        Map<Long, Integer> dislikeAdjustments = getDislikeAdjustments(request);

        if (likedPosts.contains(postId)) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "이미 추천한 게시글입니다.");
            response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
            return;
        }

        likedPosts.add(postId);
        likeAdjustments.merge(postId, 1, Integer::sum);

        if (dislikedPosts.remove(postId)) {
            decreaseCounter(dislikeAdjustments, postId);
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글을 추천했습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private void handleDislike(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long postId = RequestUtil.getLong(request, "id", null);
        if (postId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Set<Long> likedPosts = getLikedPosts(request);
        Set<Long> dislikedPosts = getDislikedPosts(request);
        Map<Long, Integer> likeAdjustments = getLikeAdjustments(request);
        Map<Long, Integer> dislikeAdjustments = getDislikeAdjustments(request);

        if (dislikedPosts.contains(postId)) {
            request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "이미 비추천한 게시글입니다.");
            response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
            return;
        }

        dislikedPosts.add(postId);
        dislikeAdjustments.merge(postId, 1, Integer::sum);

        if (likedPosts.remove(postId)) {
            decreaseCounter(likeAdjustments, postId);
        }

        request.getSession().setAttribute(MessageKey.FLASH_MESSAGE, "게시글을 비추천했습니다.");
        response.sendRedirect(request.getContextPath() + "/board/detail?id=" + postId);
    }

    private List<AttachmentDTO> saveUploadedAttachments(HttpServletRequest request, String fieldName) throws IOException, ServletException {
        List<AttachmentDTO> attachments = new ArrayList<>();
        Path uploadDirectory = UploadPathUtil.getAttachmentsDirectory();

        for (Part part : request.getParts()) {
            if (!fieldName.equals(part.getName()) || part.getSize() <= 0) {
                continue;
            }

            String originalName = part.getSubmittedFileName();
            String extension = getExtension(originalName);
            String savedName = UUID.randomUUID() + extension;
            Path target = uploadDirectory.resolve(savedName);

            try (var inputStream = part.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            AttachmentDTO attachment = new AttachmentDTO();
            attachment.setAttachmentId(System.nanoTime());
            attachment.setOriginalName(originalName);
            attachment.setSavedName(savedName);
            attachment.setFilePath(target.toString());
            attachment.setFileSize(part.getSize());
            attachment.setImage(part.getContentType() != null && part.getContentType().startsWith("image/"));
            attachments.add(attachment);
        }
        return attachments;
    }

    private String appendInlineImages(HttpServletRequest request, String content, List<AttachmentDTO> contentImages) {
        if (contentImages == null || contentImages.isEmpty()) {
            return content;
        }

        StringBuilder builder = new StringBuilder(content);
        List<AttachmentDTO> remainingImages = new ArrayList<>();
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
                remainingImages.add(image);
            }
        }

        if (!remainingImages.isEmpty()) {
            builder.append("\n<div class=\"content-image-group\">");
            for (AttachmentDTO image : remainingImages) {
                builder.append(buildInlineImageTag(request, image));
            }
            builder.append("</div>");
        }
        return builder.toString();
    }

    private String buildInlineImageTag(HttpServletRequest request, AttachmentDTO image) {
        return "<img src=\""
                + request.getContextPath()
                + "/uploads/attachments/"
                + image.getSavedName()
                + "\" alt=\""
                + escapeHtmlAttribute(image.getOriginalName())
                + "\">";
    }

    private String escapeHtmlAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> getLikeAdjustments(HttpServletRequest request) {
        Object stored = request.getServletContext().getAttribute("likeAdjustments");
        if (stored instanceof Map<?, ?>) {
            return (Map<Long, Integer>) stored;
        }
        Map<Long, Integer> map = new HashMap<>();
        request.getServletContext().setAttribute("likeAdjustments", map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> getDislikeAdjustments(HttpServletRequest request) {
        Object stored = request.getServletContext().getAttribute("dislikeAdjustments");
        if (stored instanceof Map<?, ?>) {
            return (Map<Long, Integer>) stored;
        }
        Map<Long, Integer> map = new HashMap<>();
        request.getServletContext().setAttribute("dislikeAdjustments", map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> getViewAdjustments(HttpServletRequest request) {
        Object stored = request.getServletContext().getAttribute("viewAdjustments");
        if (stored instanceof Map<?, ?>) {
            return (Map<Long, Integer>) stored;
        }
        Map<Long, Integer> map = new HashMap<>();
        request.getServletContext().setAttribute("viewAdjustments", map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getLikedPosts(HttpServletRequest request) {
        Object stored = request.getSession().getAttribute("likedPosts");
        if (stored instanceof Set<?>) {
            return (Set<Long>) stored;
        }
        Set<Long> set = new HashSet<>();
        request.getSession().setAttribute("likedPosts", set);
        return set;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getDislikedPosts(HttpServletRequest request) {
        Object stored = request.getSession().getAttribute("dislikedPosts");
        if (stored instanceof Set<?>) {
            return (Set<Long>) stored;
        }
        Set<Long> set = new HashSet<>();
        request.getSession().setAttribute("dislikedPosts", set);
        return set;
    }

    private void deletePhysicalAttachments(PostDTO post) {
        if (post == null || post.getAttachments() == null) {
            return;
        }

        for (AttachmentDTO attachment : post.getAttachments()) {
            if (attachment.getFilePath() == null || attachment.getFilePath().isBlank()) {
                continue;
            }
            try {
                Files.deleteIfExists(Path.of(attachment.getFilePath()));
            } catch (IOException ignored) {
            }
        }
    }

    private Long resolveLoginUserId(HttpSession session) {
        UserDTO loginUser = (UserDTO) session.getAttribute("loginUser");
        if (loginUser != null && loginUser.getUserId() != null) {
            return loginUser.getUserId();
        }
        return null;
    }

    private boolean canManagePost(HttpServletRequest request, PostDTO post) {
        if (post == null) {
            return false;
        }

        if (isAdmin(request.getSession())) {
            return true;
        }

        Long loginUserId = resolveLoginUserId(request.getSession());
        if (loginUserId != null && post.getUserId() != null && loginUserId.equals(post.getUserId())) {
            return true;
        }

        return false;
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

    private void applyPostCounters(HttpServletRequest request, PostDTO post) {
        if (post == null) {
            return;
        }
        post.setViewCount(post.getBaseViewCount() + getViewAdjustments(request).getOrDefault(post.getPostId(), 0));
        post.setLikeCount(post.getBaseLikeCount() + getLikeAdjustments(request).getOrDefault(post.getPostId(), 0));
        post.setDislikeCount(post.getBaseDislikeCount() + getDislikeAdjustments(request).getOrDefault(post.getPostId(), 0));
    }

    private void incrementViewCount(HttpServletRequest request, Long postId) {
        if (postId == null) {
            return;
        }
        getViewAdjustments(request).merge(postId, 1, Integer::sum);
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void clearReactions(HttpServletRequest request, Long postId) {
        getViewAdjustments(request).remove(postId);
        getLikeAdjustments(request).remove(postId);
        getDislikeAdjustments(request).remove(postId);
        getLikedPosts(request).remove(postId);
        getDislikedPosts(request).remove(postId);
    }

    private void decreaseCounter(Map<Long, Integer> counterMap, Long postId) {
        Integer current = counterMap.get(postId);
        if (current == null) {
            return;
        }
        if (current <= 1) {
            counterMap.remove(postId);
            return;
        }
        counterMap.put(postId, current - 1);
    }

    private boolean isAdmin(HttpSession session) {
        UserDTO loginUser = (UserDTO) session.getAttribute("loginUser");
        if (loginUser == null || loginUser.getRoleCode() == null) {
            return false;
        }
        return RoleCode.ADMIN.equals(loginUser.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(loginUser.getRoleCode());
    }
}
