<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>404 | REDWEB</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body class="simple-page">
<main class="compact-card">
    <h1>페이지를 찾을 수 없습니다</h1>
    <p>주소가 변경되었거나 삭제된 페이지일 수 있습니다.</p>
    <a class="btn btn-primary" href="${pageContext.request.contextPath}/home">메인으로 이동</a>
</main>
</body>
</html>

