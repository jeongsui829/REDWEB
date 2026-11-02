<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.board.dto.PostDTO" %>
<%
    String keyword = (String) request.getAttribute("keyword");
    List<PostDTO> results = (List<PostDTO>) request.getAttribute("results");
    int resultCount = results != null ? results.size() : 0;
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>검색 결과 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/board.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="page-shell container board-shell">
    <section class="board-hero section-card">
        <div class="board-hero-copy">
            <span class="eyebrow">검색</span>
            <h1>검색 결과 <%= resultCount %>건</h1>
            <p><strong><%= keyword == null || keyword.isBlank() ? "검색어 없음" : keyword %></strong>에 대한 결과입니다.</p>
        </div>
    </section>
    <section class="section-card board-list-card">
        <div class="list-table">
            <% if (results != null && !results.isEmpty()) {
                for (PostDTO post : results) { %>
                    <a class="board-row" href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>">
                        <div class="board-row-main">
                            <div class="title-line"><strong><%= post.getTitle() %></strong></div>
                            <p><%= post.getContent() %></p>
                        </div>
                        <div class="board-row-meta">
                            <span><%= post.getAuthorNickname() %></span>
                            <span><%= post.getBoardName() %></span>
                            <span>조회 <%= post.getViewCount() %></span>
                        </div>
                    </a>
            <%  }
               } else { %>
                    <div class="board-row">
                        <div class="board-row-main">
                            <strong>검색 결과가 없습니다.</strong>
                            <p>다른 검색어를 입력하거나 다른 게시판을 둘러보세요.</p>
                        </div>
                    </div>
            <% } %>
        </div>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
