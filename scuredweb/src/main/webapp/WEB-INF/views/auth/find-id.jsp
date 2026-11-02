<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
%>
<%
    String errorMessage = (String) session.getAttribute("errorMessage");
    String flashMessage = (String) session.getAttribute("flashMessage");
    if (errorMessage != null) session.removeAttribute("errorMessage");
    if (flashMessage != null) session.removeAttribute("flashMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <title>아이디 찾기 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/pages/auth.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>아이디 찾기</h1>
    <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
    <% if (flashMessage != null) { %><div class="alert success"><%= h(flashMessage) %></div><% } %>
    <form class="stack-form" method="post" action="${pageContext.request.contextPath}/find-id">
        <%= CsrfUtil.hiddenInput(request) %>
        <label>이름<input type="text" name="userName" required></label>
        <label>이메일<input type="email" name="email" required></label>
        <button class="btn btn-primary full" type="submit">아이디 조회</button>
    </form>
    <div class="auth-links">
        <a href="${pageContext.request.contextPath}/login">로그인으로 돌아가기</a>
    </div>
</main>
</body>
</html>
