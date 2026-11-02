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
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>회원가입 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/auth.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="auth-shell container">
    <section class="auth-card wide">
        <div class="auth-copy">
            <span class="eyebrow">REDWEB 가입</span>
            <h1>회원가입</h1>
            <p>계정을 만들고 게시글 작성, 쪽지 전송, 프로필 관리, 커뮤니티 참여를 시작하세요.</p>
        </div>
        <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
        <form class="stack-form grid-form" method="post" action="<%= request.getContextPath() %>/register">
            <%= CsrfUtil.hiddenInput(request) %>
            <label>아이디
                <input type="text" name="loginId" maxlength="50" required>
            </label>
            <label>이름
                <input type="text" name="userName" maxlength="100" required>
            </label>
            <label>닉네임
                <input type="text" name="nickname" maxlength="100" required>
            </label>
            <label>이메일
                <input type="email" name="email" maxlength="150" required>
            </label>
            <label>전화번호
                <input type="text" name="phone" maxlength="30" placeholder="010-0000-0000">
            </label>
            <label>비밀번호
                <input type="password" maxlength="128" name="password" autocomplete="new-password" required>
            </label>
            <label>비밀번호 찾기 질문
                <input type="text" name="securityQuestion" maxlength="300" required>
            </label>
            <label>비밀번호 찾기 답변
                <input type="text" name="securityAnswer" maxlength="300" required>
            </label>
            <button class="btn btn-primary full" type="submit">회원가입</button>
        </form>
        <div class="auth-links">
            <a href="<%= request.getContextPath() %>/login">이미 계정이 있으신가요?</a>
        </div>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
