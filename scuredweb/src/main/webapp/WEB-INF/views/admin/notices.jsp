<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil, com.scuredweb.board.dto.BoardDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
%>
<%
    String flashMessage = (String) request.getAttribute("flashMessage");
    List<BoardDTO> boards = (List<BoardDTO>) request.getAttribute("boards");
    boolean boardsLoadError = Boolean.TRUE.equals(request.getAttribute("boardsLoadError"));
    boolean boardsUnavailable = boardsLoadError || boards == null || boards.isEmpty();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>공지 등록 | REDWEB</title>
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
                    <h2>공지 등록</h2>
                    <p>게시판을 선택하고 해당 게시판 상단에 노출될 공지를 등록합니다.</p>
                </div>
            </div>
            <% if (flashMessage != null && !flashMessage.isBlank()) { %><div class="admin-alert"><%= h(flashMessage) %></div><% } %>
            <% if (boardsUnavailable) { %><div class="admin-alert" role="alert"><%= boardsLoadError
                    ? "게시판 정보를 불러오지 못해 공지를 등록할 수 없습니다."
                    : "등록 가능한 게시판이 없습니다." %></div><% } %>
            <form class="admin-form" method="post" action="<%= request.getContextPath() %>/admin/notices/create" data-confirm="이 공지를 등록하시겠습니까?">
                <%= CsrfUtil.hiddenInput(request) %>
                <label><span>공지 게시판</span>
                    <select name="boardCode" required <%= boardsUnavailable ? "disabled" : "" %>>
                        <% if (boards != null) { for (BoardDTO board : boards) { %>
                            <option value="<%= a(board.getBoardCode()) %>"><%= h(board.getBoardName()) %></option>
                        <% }} %>
                    </select>
                </label>
                <label><span>공지 제목</span><input type="text" name="title" maxlength="200" required></label>
                <label><span>공지 내용</span><textarea name="content" rows="10" required></textarea></label>
                <div class="admin-form-actions">
                    <button type="submit" class="btn btn-primary" <%= boardsUnavailable ? "disabled" : "" %>>공지 등록</button>
                </div>
            </form>
        </section>
    </main>
</div>
<script src="<%= request.getContextPath() %>/assets/js/common.js"></script>
</body>
</html>
