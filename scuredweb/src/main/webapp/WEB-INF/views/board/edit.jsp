<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil, com.scuredweb.board.dto.PostDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
%>
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
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="page-shell container board-write-shell">
    <section class="section-card editor-card">
        <span class="eyebrow">콘텐츠 편집기</span>
        <h1>게시글 수정</h1>
        <form class="stack-form" method="post" action="<%= request.getContextPath() %>/board/edit" enctype="multipart/form-data">
            <%= CsrfUtil.hiddenInput(request) %>
            <input type="hidden" name="id" value="<%= post != null ? post.getPostId() : "" %>">
            <label>제목
                <input type="text" name="title" maxlength="200" value="<%= a(post != null ? post.getTitle() : "") %>" required>
            </label>
            <label>내용
                <textarea name="content" rows="16" maxlength="20000" required><%= h(post != null ? post.getContent() : "") %></textarea>
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
</body>
</html>
