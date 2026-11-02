<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.user.dto.UserDTO" %>
<%
    List<UserDTO> users = (List<UserDTO>) request.getAttribute("users");
    String flashMessage = (String) request.getAttribute("flashMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>회원 관리 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/admin/admin.css">
</head>
<body class="admin-body">
<jsp:include page="/WEB-INF/views/common/admin-header.jsp" />
<div class="admin-layout">
    <jsp:include page="/WEB-INF/views/common/admin-sidebar.jsp" />
    <main class="admin-main">
        <section class="admin-section">
            <div class="section-head">
                <div>
                    <h2>회원 관리</h2>
                    <p>관리자 콘솔에서 회원 권한과 계정 상태를 확인합니다.</p>
                </div>
            </div>
            <% if (flashMessage != null && !flashMessage.isBlank()) { %><div class="admin-alert"><%= flashMessage %></div><% } %>
            <div class="admin-table-wrap">
                <table class="admin-table">
                    <thead>
                    <tr>
                        <th>번호</th><th>아이디</th><th>이름</th><th>닉네임</th><th>이메일</th><th>권한</th><th>상태</th><th>관리</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% if (users != null) { for (UserDTO user : users) {
                        boolean admin = "ADMIN".equals(user.getRoleCode()) || "SUPER_ADMIN".equals(user.getRoleCode());
                        boolean active = "ACTIVE".equals(user.getStatus());
                    %>
                        <tr>
                            <td><%= user.getUserId() %></td>
                            <td><%= user.getLoginId() %></td>
                            <td><%= user.getUserName() %></td>
                            <td><%= user.getNickname() %></td>
                            <td><%= user.getEmail() %></td>
                            <td><span class="status-badge <%= admin ? "is-admin" : "is-member" %>"><%= admin ? "관리자" : "회원" %></span></td>
                            <td><span class="status-badge <%= active ? "is-active" : "is-suspended" %>"><%= active ? "활성" : user.getStatus() %></span></td>
                            <td>
                                <div class="admin-actions">
                                    <form method="post" action="<%= request.getContextPath() %>/admin/users/status" data-confirm="이 회원을 활성화하시겠습니까?">
                                        <input type="hidden" name="userId" value="<%= user.getUserId() %>">
                                        <input type="hidden" name="status" value="ACTIVE">
                                        <button type="submit" class="btn btn-secondary">활성화</button>
                                    </form>
                                    <form method="post" action="<%= request.getContextPath() %>/admin/users/status" data-confirm="이 회원을 정지하시겠습니까?">
                                        <input type="hidden" name="userId" value="<%= user.getUserId() %>">
                                        <input type="hidden" name="status" value="SUSPENDED">
                                        <button type="submit" class="btn btn-danger">정지</button>
                                    </form>
                                </div>
                            </td>
                        </tr>
                    <% }} %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
<script src="<%= request.getContextPath() %>/assets/js/common.js"></script>
</body>
</html>
