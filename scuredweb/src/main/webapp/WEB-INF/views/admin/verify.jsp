<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
%>
<%
    String redirect = (String) request.getAttribute("redirect");
    String errorMessage = (String) request.getAttribute("errorMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>관리자 인증 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/admin/admin.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <span class="eyebrow">관리자 2차 인증</span>
    <h1>관리자 영역에 접근하려면 비밀번호를 다시 확인해야 합니다.</h1>
    <p>회원 상태 변경, 게시글 숨김/삭제, 공지 등록 기능은 추가 인증 후 사용할 수 있습니다.</p>
    <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
    <form class="stack-form" method="post" action="<%= request.getContextPath() %>/admin/verify">
        <%= CsrfUtil.hiddenInput(request) %>
        <input type="hidden" name="redirect" value="<%= a(redirect) %>">
        <label>관리자 비밀번호
            <input type="password" maxlength="128" name="password" autocomplete="current-password" placeholder="관리자 비밀번호를 입력하세요." required>
        </label>
        <button type="submit" class="btn btn-primary full">관리자 영역으로 이동</button>
    </form>
</main>
</body>
</html>
