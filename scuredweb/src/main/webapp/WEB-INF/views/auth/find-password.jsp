<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
%>
<%
    String errorMessage = (String) session.getAttribute("errorMessage");
    if (errorMessage != null) session.removeAttribute("errorMessage");
    String reset = request.getParameter("reset");
    String resetMessage = null;
    if ("limited".equals(reset)) {
        resetMessage = "요청이 일시적으로 제한되었습니다. 잠시 후 다시 시도하세요.";
    } else if ("invalid".equals(reset)) {
        resetMessage = "비밀번호 재설정 요청이 잘못되었거나 만료되었습니다.";
    }
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <title>비밀번호 찾기 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/pages/auth.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>비밀번호 찾기</h1>
    <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
    <% if (resetMessage != null) { %><div class="alert error"><%= h(resetMessage) %></div><% } %>
    <form class="stack-form" method="post" action="${pageContext.request.contextPath}/find-password">
        <%= CsrfUtil.hiddenInput(request) %>
        <label>아이디<input type="text" name="loginId" required></label>
        <label>이메일<input type="email" name="email" required></label>
        <button class="btn btn-primary full" type="submit">재설정 안내 받기</button>
    </form>
    <div class="auth-links">
        <a href="${pageContext.request.contextPath}/login">로그인으로 돌아가기</a>
    </div>
</main>
</body>
</html>

