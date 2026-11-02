<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
%>
<%
    String errorMessage = (String) session.getAttribute("errorMessage");
    if (errorMessage != null) session.removeAttribute("errorMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <title>비밀번호 재설정 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/pages/auth.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>비밀번호 재설정</h1>
    <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
    <form class="stack-form" method="post" action="${pageContext.request.contextPath}/reset-password">
        <%= CsrfUtil.hiddenInput(request) %>
        <label>새 비밀번호<input type="password" maxlength="128" name="newPassword" minlength="15" required></label>
        <label>새 비밀번호 확인<input type="password" maxlength="128" name="confirmPassword" minlength="15" required></label>
        <button class="btn btn-primary full" type="submit">비밀번호 변경</button>
    </form>
</main>
</body>
</html>

