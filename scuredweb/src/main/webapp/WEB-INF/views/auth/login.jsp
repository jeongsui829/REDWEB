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
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>로그인 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/auth.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="auth-shell container">
    <section class="auth-card">
        <div class="auth-copy">
            <span class="eyebrow">다시 오신 것을 환영합니다</span>
            <h1>REDWEB 로그인</h1>
            <p>보안 포럼, CTF 기록, 프로젝트 토론에 참여하려면 로그인하세요.</p>
        </div>
        <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
        <% if (flashMessage != null) { %><div class="alert success"><%= h(flashMessage) %></div><% } %>
        <form class="stack-form" method="post" action="<%= request.getContextPath() %>/login">
            <%= CsrfUtil.hiddenInput(request) %>
            <label>아이디
                <input type="text" name="loginId" autocomplete="username" required>
            </label>
            <label>비밀번호
                <input type="password" maxlength="128" name="password" autocomplete="current-password" required>
            </label>
            <button class="btn btn-primary full" type="submit">로그인</button>
        </form>
        <div class="auth-links">
            <% if (Boolean.TRUE.equals(application.getAttribute("publicRegistrationEnabled"))) { %><a href="<%= request.getContextPath() %>/register">회원가입</a><% } %>
            <a href="<%= request.getContextPath() %>/find-id">아이디 찾기</a>
            <a href="<%= request.getContextPath() %>/find-password">비밀번호 찾기</a>
        </div>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
