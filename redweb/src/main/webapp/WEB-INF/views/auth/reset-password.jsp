<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String errorMessage = (String) session.getAttribute("errorMessage");
    String resetQuestion = (String) session.getAttribute("resetQuestion");
    if (errorMessage != null) session.removeAttribute("errorMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>비밀번호 재설정 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/pages/auth.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>비밀번호 재설정</h1>
    <% if (errorMessage != null) { %><div class="alert error"><%= errorMessage %></div><% } %>
    <% if (resetQuestion == null) { %>
        <p>비밀번호 찾기 화면에서 계정을 먼저 확인해 주세요.</p>
        <a class="btn btn-primary full" href="${pageContext.request.contextPath}/find-password">비밀번호 찾기로 이동</a>
    <% } else { %>
        <form class="stack-form" method="post" action="${pageContext.request.contextPath}/reset-password">
            <label>보안 질문
                <input type="text" value="<%= resetQuestion %>" readonly>
            </label>
            <label>보안 답변<input type="text" name="securityAnswer" required></label>
            <label>새 비밀번호<input type="password" name="newPassword" minlength="8" required></label>
            <label>새 비밀번호 확인<input type="password" name="confirmPassword" minlength="8" required></label>
            <button class="btn btn-primary full" type="submit">비밀번호 변경</button>
        </form>
    <% } %>
</main>
</body>
</html>
