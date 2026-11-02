<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil, com.scuredweb.board.dto.BoardDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
%>
<%
    List<BoardDTO> boards = (List<BoardDTO>) request.getAttribute("boards");
    String flashMessage = (String) request.getAttribute("flashMessage");
    String errorMessage = (String) request.getAttribute("errorMessage");
    boolean boardsLoadError = Boolean.TRUE.equals(request.getAttribute("boardsLoadError"));
    boolean boardsUnavailable = boardsLoadError || boards == null || boards.isEmpty();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>게시글 작성 | REDWEB</title>
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
        <h1>게시글 작성</h1>
        <% if (flashMessage != null) { %>
            <div class="alert error"><%= h(flashMessage) %></div>
        <% } %>
        <% if (errorMessage != null) { %>
            <div class="alert error" role="alert"><%= h(errorMessage) %></div>
        <% } %>
        <% if (boardsUnavailable) { %>
            <div class="alert error" role="alert"><%= boardsLoadError
                    ? "게시판 정보를 불러오지 못해 글을 등록할 수 없습니다."
                    : "등록 가능한 게시판이 없습니다." %></div>
        <% } %>
        <form class="stack-form" method="post" action="<%= request.getContextPath() %>/board/write" enctype="multipart/form-data">
            <%= CsrfUtil.hiddenInput(request) %>
            <label>게시판
                <select name="boardCode" required <%= boardsUnavailable ? "disabled" : "" %>>
                    <% if (boards != null) { for (BoardDTO board : boards) { %>
                        <option value="<%= a(board.getBoardCode()) %>"><%= h(board.getBoardName()) %></option>
                    <% }} %>
                </select>
            </label>
            <label>제목
                <input type="text" name="title" maxlength="200" required>
            </label>
            <label>내용
                <textarea name="content" rows="16" maxlength="20000" placeholder="내용을 입력하세요. 코드, 힌트, 설명을 함께 작성할 수 있습니다." required></textarea>
            </label>
            <label>본문 이미지
                <input type="file" name="contentImages" accept="image/*" multiple>
            </label>
            <label>첨부 파일
                <input type="file" name="attachments" multiple>
            </label>
            <button type="submit" class="btn btn-primary" <%= boardsUnavailable ? "disabled" : "" %>>게시글 등록</button>
        </form>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
