<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String errorMessage = (String) session.getAttribute("errorMessage");
    if (errorMessage != null) {
        session.removeAttribute("errorMessage");
    }
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
        <% if (errorMessage != null) { %><div class="alert error"><%= errorMessage %></div><% } %>
        <form class="stack-form grid-form" method="post" action="<%= request.getContextPath() %>/register">
            <label>아이디
                <input type="text" name="loginId" placeholder="사용할 아이디" autocomplete="off" required>
            </label>
            <label>이름
                <input type="text" name="userName" placeholder="실명 또는 표시 이름" autocomplete="name" required>
            </label>
            <label>닉네임
                <input type="text" name="nickname" placeholder="서비스에서 사용할 닉네임" autocomplete="off" required>
            </label>
            <label>이메일
                <input type="email" name="email" placeholder="example@redweb.local" autocomplete="email" required>
            </label>
            <label>전화번호
                <input type="tel" name="phone" placeholder="010-1234-5678" autocomplete="tel" required>
            </label>
            <label>비밀번호
                <input type="password" name="password" minlength="8" placeholder="8자 이상 입력" autocomplete="new-password" required>
            </label>
            <label>비밀번호 찾기 질문
                <select name="securityQuestion" required>
                    <option value="">질문을 선택하세요</option>
                    <option>초등학교 때 가장 친했던 친구의 이름은?</option>
                    <option>처음 키운 반려동물의 이름은?</option>
                    <option>어린 시절 살았던 동네 이름은?</option>
                    <option>가장 기억에 남는 선생님의 성함은?</option>
                    <option>처음 다녔던 학교의 이름은?</option>
                    <option>어릴 때 가장 좋아했던 음식은?</option>
                    <option>처음 사용했던 휴대폰 기종이나 별명은?</option>
                    <option>가족만 알고 있는 나의 별명은?</option>
                    <option>가장 기억에 남는 여행지는?</option>
                    <option>내가 처음으로 좋아했던 영화나 만화 제목은?</option>
                </select>
            </label>
            <label>비밀번호 찾기 답변
                <input type="text" name="securityAnswer" placeholder="본인만 알 수 있는 답변" autocomplete="off" required>
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
