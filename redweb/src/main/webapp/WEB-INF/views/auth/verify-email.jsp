<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>이메일 인증 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>이메일 인증</h1>
    <form class="stack-form">
        <label>인증 코드<input type="text" required></label>
        <button class="btn btn-primary full" type="submit">인증 완료</button>
    </form>
</main>
</body>
</html>

