<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.board.dto.PostDTO" %>
<%
    List<PostDTO> posts = (List<PostDTO>) request.getAttribute("posts");
    String flashMessage = (String) request.getAttribute("flashMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>게시글 관리 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/admin/admin.css">
</head>
<body class="admin-body">
<jsp:include page="/WEB-INF/views/common/admin-header.jsp" />
<div class="admin-layout">
    <jsp:include page="/WEB-INF/views/common/admin-sidebar.jsp" />
    <main class="admin-main">
        <section class="admin-section">
            <div class="section-head">
                <div>
                    <h2>게시글 관리</h2>
                    <p>공개 게시글과 공지를 확인하고 필요한 경우 숨김, 복구, 삭제를 처리합니다.</p>
                </div>
            </div>
            <% if (flashMessage != null && !flashMessage.isBlank()) { %><div class="admin-alert"><%= flashMessage %></div><% } %>
            <div class="admin-table-wrap">
                <table class="admin-table">
                    <thead>
                    <tr>
                        <th>번호</th><th>게시판</th><th>제목</th><th>작성자</th><th>조회</th><th>추천</th><th>상태</th><th>관리</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% if (posts != null) { for (PostDTO post : posts) {
                        boolean hidden = "HIDDEN".equals(post.getStatus());
                    %>
                        <tr>
                            <td><%= post.getPostId() %></td>
                            <td><%= post.getBoardName() %></td>
                            <td><a class="admin-post-link" href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>"><%= post.getTitle() %></a></td>
                            <td><%= post.getAuthorNickname() %></td>
                            <td><%= post.getViewCount() %></td>
                            <td><%= post.getLikeCount() %></td>
                            <td><span class="status-badge <%= hidden ? "is-suspended" : "is-active" %>"><%= hidden ? "숨김" : (post.isNotice() ? "공지" : "공개") %></span></td>
                            <td>
                                <div class="admin-actions">
                                    <% if (hidden) { %>
                                        <form method="post" action="<%= request.getContextPath() %>/admin/posts/restore" data-confirm="이 게시글을 다시 공개하시겠습니까?">
                                            <input type="hidden" name="postId" value="<%= post.getPostId() %>">
                                            <button type="submit" class="btn btn-secondary">복구</button>
                                        </form>
                                    <% } else { %>
                                        <form method="post" action="<%= request.getContextPath() %>/admin/posts/hide" data-confirm="이 게시글을 숨기시겠습니까?">
                                            <input type="hidden" name="postId" value="<%= post.getPostId() %>">
                                            <button type="submit" class="btn btn-secondary">숨김</button>
                                        </form>
                                    <% } %>
                                    <form method="post" action="<%= request.getContextPath() %>/admin/posts/delete" data-confirm="이 게시글을 삭제하시겠습니까?">
                                        <input type="hidden" name="postId" value="<%= post.getPostId() %>">
                                        <button type="submit" class="btn btn-danger">삭제</button>
                                    </form>
                                </div>
                            </td>
                        </tr>
                    <% }} %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
<script src="<%= request.getContextPath() %>/assets/js/common.js"></script>
</body>
</html>
