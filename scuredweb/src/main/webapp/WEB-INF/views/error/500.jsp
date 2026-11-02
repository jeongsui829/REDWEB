<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <title>500 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>일시적인 오류가 발생했습니다</h1>
    <p>잠시 후 다시 시도하거나 메인 화면으로 이동해 주세요.</p>
    <a class="btn btn-primary" href="${pageContext.request.contextPath}/home">메인으로 이동</a>
</main>
</body>
</html>
