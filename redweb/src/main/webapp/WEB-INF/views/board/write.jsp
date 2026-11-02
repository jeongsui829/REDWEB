<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.board.dto.BoardDTO" %>
<%
    List<BoardDTO> boards = (List<BoardDTO>) request.getAttribute("boards");
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
        <form class="stack-form" method="post" action="<%= request.getContextPath() %>/board/write" enctype="multipart/form-data">
            <label>게시판
                <select name="boardCode">
                    <% if (boards != null) { for (BoardDTO board : boards) { %>
                        <option value="<%= board.getBoardCode() %>"><%= board.getBoardName() %></option>
                    <% }} %>
                </select>
            </label>
            <label>제목
                <input type="text" name="title" required>
            </label>
            <label>내용
                <textarea name="content" rows="16" placeholder="내용을 입력하세요. 코드, 힌트, 설명을 함께 작성할 수 있습니다."></textarea>
            </label>
            <label>본문 이미지
                <input type="file" name="contentImages" accept="image/*" multiple>
            </label>
            <label>첨부 파일
                <input type="file" name="attachments" multiple>
            </label>
            <button type="submit" class="btn btn-primary">게시글 등록</button>
        </form>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
