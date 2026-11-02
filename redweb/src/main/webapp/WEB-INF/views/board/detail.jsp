<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, java.time.format.DateTimeFormatter, com.redweb.board.dto.PostDTO, com.redweb.comment.dto.CommentDTO, com.redweb.file.dto.AttachmentDTO, com.redweb.user.dto.UserDTO, com.redweb.common.constant.SessionKey, com.redweb.common.constant.RoleCode" %>
<%
    PostDTO post = (PostDTO) request.getAttribute("post");
    List<CommentDTO> comments = (List<CommentDTO>) request.getAttribute("comments");
    Boolean recommended = (Boolean) request.getAttribute("recommended");
    Boolean disliked = (Boolean) request.getAttribute("disliked");
    Boolean canManagePost = (Boolean) request.getAttribute("canManagePost");
    String flashMessage = (String) request.getAttribute("flashMessage");
    DateTimeFormatter commentFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
    UserDTO loginUser = (UserDTO) session.getAttribute(SessionKey.LOGIN_USER);
    boolean commentAdmin = loginUser != null
            && (RoleCode.ADMIN.equals(loginUser.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(loginUser.getRoleCode()));
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= post != null ? post.getTitle() : "게시글 상세" %> | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/board.css">
</head>
<body class="board-detail-page">
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="page-shell container detail-shell">
    <article class="section-card detail-card">
        <span class="eyebrow"><%= post != null ? post.getBoardName() : "게시판" %> | <%= post != null ? post.getCategoryName() : "일반" %></span>
        <h1><%= post != null ? post.getTitle() : "게시글 상세" %></h1>
        <% if (flashMessage != null) { %>
            <div class="alert success"><%= flashMessage %></div>
        <% } %>
        <div class="detail-meta">
            <span class="user-menu-trigger" data-user="<%= post != null ? post.getAuthorNickname() : "" %>"><%= post != null ? post.getAuthorNickname() : "작성자" %></span>
            <span>조회 <%= post != null ? post.getViewCount() : 0 %></span>
            <span>추천 <%= post != null ? post.getLikeCount() : 0 %></span>
            <span>비추천 <%= post != null ? post.getDislikeCount() : 0 %></span>
            <span>댓글 <%= post != null ? post.getCommentCount() : 0 %></span>
        </div>
        <div class="detail-content"><%= post != null ? post.getContent() : "" %></div>

        <% if (post != null && post.getAttachments() != null && !post.getAttachments().isEmpty()) { %>
            <div class="line-card">
                <strong>첨부파일</strong>
                <% for (AttachmentDTO attachment : post.getAttachments()) { %>
                    <a href="<%= request.getContextPath() %>/files/download?savedName=<%= attachment.getSavedName() %>&originalName=<%= java.net.URLEncoder.encode(attachment.getOriginalName(), "UTF-8") %>">
                        <%= attachment.getOriginalName() %> (<%= attachment.getFileSize() %> bytes)
                    </a>
                <% } %>
            </div>
        <% } %>

        <div class="detail-actions">
            <form method="post" action="<%= request.getContextPath() %>/board/recommend">
                <input type="hidden" name="id" value="<%= post != null ? post.getPostId() : 101 %>">
                <button class="btn btn-primary" type="submit"><%= Boolean.TRUE.equals(recommended) ? "추천 완료" : "추천" %></button>
            </form>
            <form method="post" action="<%= request.getContextPath() %>/board/dislike">
                <input type="hidden" name="id" value="<%= post != null ? post.getPostId() : 101 %>">
                <button class="btn btn-secondary" type="submit"><%= Boolean.TRUE.equals(disliked) ? "비추천 완료" : "비추천" %></button>
            </form>
            <% if (Boolean.TRUE.equals(canManagePost)) { %>
                <a class="btn btn-secondary" href="<%= request.getContextPath() %>/board/edit?id=<%= post != null ? post.getPostId() : 101 %>">수정</a>
                <form method="post" action="<%= request.getContextPath() %>/board/delete">
                    <input type="hidden" name="id" value="<%= post != null ? post.getPostId() : 101 %>">
                    <button class="btn btn-secondary" type="submit">삭제</button>
                </form>
            <% } %>
        </div>
    </article>

    <section class="section-card">
        <h2>댓글</h2>
        <form class="stack-form comment-form" method="post" action="<%= request.getContextPath() %>/comment/add">
            <input type="hidden" name="postId" value="<%= post != null ? post.getPostId() : 101 %>">
            <label>댓글 작성
                <textarea name="content" rows="4" placeholder="게시글에 대한 의견을 남겨보세요." required></textarea>
            </label>
            <button type="submit" class="btn btn-primary">댓글 등록</button>
        </form>
        <div class="comment-list">
            <% if (comments != null) {
                for (CommentDTO comment : comments) {
                    String createdAt = comment.getCreatedAt() != null ? comment.getCreatedAt().format(commentFormatter) : "";
                    String replyFormId = "reply-form-" + comment.getCommentId();
            %>
                <div class="comment-item">
                    <strong class="user-menu-trigger" data-user="<%= comment.getAuthorNickname() %>"><%= comment.getAuthorNickname() %></strong>
                    <% if (loginUser != null && (commentAdmin || (loginUser.getUserId() != null && loginUser.getUserId().equals(comment.getUserId())))) { %>
                        <form method="post" action="<%= request.getContextPath() %>/comment/delete">
                            <input type="hidden" name="postId" value="<%= post != null ? post.getPostId() : "" %>">
                            <input type="hidden" name="commentId" value="<%= comment.getCommentId() %>">
                            <button type="submit" class="reply-toggle">삭제</button>
                        </form>
                    <% } %>
                    <p><%= comment.getContent() %></p>
                    <div class="comment-meta">
                        <span><%= createdAt %> 작성</span>
                        <button type="button" class="reply-toggle" data-target="<%= replyFormId %>">답글</button>
                    </div>
                    <form id="<%= replyFormId %>" class="stack-form reply-form" method="post" action="<%= request.getContextPath() %>/comment/add" hidden>
                        <input type="hidden" name="postId" value="<%= post != null ? post.getPostId() : 101 %>">
                        <input type="hidden" name="parentCommentId" value="<%= comment.getCommentId() %>">
                        <label>답글 작성
                            <textarea name="content" rows="2" placeholder="이 댓글에 답글을 남겨보세요." required></textarea>
                        </label>
                        <button type="submit" class="btn btn-secondary">답글 등록</button>
                    </form>
                    <% for (CommentDTO child : comment.getChildren()) {
                        String childCreatedAt = child.getCreatedAt() != null ? child.getCreatedAt().format(commentFormatter) : "";
                    %>
                        <div class="comment-child">
                            <strong class="user-menu-trigger" data-user="<%= child.getAuthorNickname() %>"><%= child.getAuthorNickname() %></strong>
                            <% if (loginUser != null && (commentAdmin || (loginUser.getUserId() != null && loginUser.getUserId().equals(child.getUserId())))) { %>
                                <form method="post" action="<%= request.getContextPath() %>/comment/delete">
                                    <input type="hidden" name="postId" value="<%= post != null ? post.getPostId() : "" %>">
                                    <input type="hidden" name="commentId" value="<%= child.getCommentId() %>">
                                    <button type="submit" class="reply-toggle">삭제</button>
                                </form>
                            <% } %>
                            <p><%= child.getContent() %></p>
                            <div class="comment-meta">
                                <span><%= childCreatedAt %> 작성</span>
                            </div>
                        </div>
                    <% } %>
                </div>
            <%  }
               } %>
        </div>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
<script src="<%= request.getContextPath() %>/assets/js/board-detail.js"></script>
</body>
</html>
