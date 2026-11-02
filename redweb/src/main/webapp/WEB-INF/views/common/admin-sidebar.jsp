<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String currentAdminPath = request.getServletPath();
%>
<aside class="admin-sidebar" aria-label="관리자 메뉴">
    <a class="<%= "/admin/dashboard".equals(currentAdminPath) ? "active" : "" %>"
       href="${pageContext.request.contextPath}/admin/dashboard"
       <%= "/admin/dashboard".equals(currentAdminPath) ? "aria-current=\"page\"" : "" %>>대시보드</a>
    <a class="<%= "/admin/users".equals(currentAdminPath) ? "active" : "" %>"
       href="${pageContext.request.contextPath}/admin/users"
       <%= "/admin/users".equals(currentAdminPath) ? "aria-current=\"page\"" : "" %>>회원 관리</a>
    <a class="<%= "/admin/posts".equals(currentAdminPath) ? "active" : "" %>"
       href="${pageContext.request.contextPath}/admin/posts"
       <%= "/admin/posts".equals(currentAdminPath) ? "aria-current=\"page\"" : "" %>>게시글 관리</a>
    <a class="<%= "/admin/reports".equals(currentAdminPath) ? "active" : "" %>"
       href="${pageContext.request.contextPath}/admin/reports"
       <%= "/admin/reports".equals(currentAdminPath) ? "aria-current=\"page\"" : "" %>>신고 관리</a>
    <a class="<%= "/admin/notices".equals(currentAdminPath) ? "active" : "" %>"
       href="${pageContext.request.contextPath}/admin/notices"
       <%= "/admin/notices".equals(currentAdminPath) ? "aria-current=\"page\"" : "" %>>공지 등록</a>
</aside>
