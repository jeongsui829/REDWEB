<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String errorMessage = (String) session.getAttribute("errorMessage");
    if (errorMessage != null) session.removeAttribute("errorMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>비밀번호 찾기 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/pages/auth.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>비밀번호 찾기</h1>
    <% if (errorMessage != null) { %><div class="alert error"><%= errorMessage %></div><% } %>
    <form class="stack-form" method="post" action="${pageContext.request.contextPath}/find-password">
        <label>아이디<input type="text" name="loginId" required></label>
        <label>이메일<input type="email" name="email" required></label>
        <button class="btn btn-primary full" type="submit">보안 질문 확인</button>
    </form>
    <div class="auth-links">
        <a href="${pageContext.request.contextPath}/login">로그인으로 돌아가기</a>
    </div>
</main>
</body>
</html>

