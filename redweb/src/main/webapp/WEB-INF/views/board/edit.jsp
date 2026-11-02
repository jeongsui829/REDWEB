<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.redweb.board.dto.PostDTO" %>
<%
    PostDTO post = (PostDTO) request.getAttribute("post");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>게시글 수정 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/board.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/editor-compat.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="page-shell container board-write-shell">
    <section class="section-card editor-card">
        <span class="eyebrow">콘텐츠 편집기</span>
        <h1>게시글 수정</h1>
        <form id="postEditForm" class="stack-form" method="post" action="<%= request.getContextPath() %>/board/edit" enctype="multipart/form-data">
            <input type="hidden" name="id" value="<%= post != null ? post.getPostId() : 101 %>">
            <label>제목
                <input type="text" name="title" value="<%= post != null ? post.getTitle() : "" %>" required>
            </label>
            <label>내용
                <div id="richEditor" class="target-raw-editor" contenteditable="true" role="textbox" aria-multiline="true" tabindex="0"><%= post != null && post.getContent() != null ? post.getContent() : "" %></div>
                <textarea id="postContent" name="content" hidden></textarea>
            </label>
            <label>본문 이미지
                <input type="file" name="contentImages" accept="image/*" multiple>
            </label>
            <label>첨부파일
                <input type="file" name="attachments" multiple>
            </label>
            <button type="submit" class="btn btn-primary">변경사항 저장</button>
        </form>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
<script src="<%= request.getContextPath() %>/assets/js/editor-compat.js"></script>
</body>
</html>
