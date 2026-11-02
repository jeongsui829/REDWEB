<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.common.util.HtmlUtil, com.scuredweb.admin.dto.DashboardMetricDTO, com.scuredweb.board.dto.PostDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
%>
<%
    List<DashboardMetricDTO> metrics = (List<DashboardMetricDTO>) request.getAttribute("metrics");
    List<PostDTO> recentPosts = (List<PostDTO>) request.getAttribute("recentPosts");
    boolean metricsLoadError = Boolean.TRUE.equals(request.getAttribute("metricsLoadError"));
    boolean recentPostsLoadError = Boolean.TRUE.equals(request.getAttribute("recentPostsLoadError"));
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>관리자 대시보드 | REDWEB</title>
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
                    <h2>대시보드</h2>
                    <p>현재 서비스 상태와 최근 활동을 한눈에 확인합니다.</p>
                </div>
            </div>
            <% if (metricsLoadError) { %><div class="admin-alert" role="alert">통계를 불러오지 못했습니다.</div><% } %>
            <div class="metric-grid">
                <% if (metrics != null) { for (DashboardMetricDTO metric : metrics) { %>
                    <article class="metric-card">
                        <span><%= h(metric.getLabel()) %></span>
                        <strong><%= h(metric.getValue()) %></strong>
                        <small><%= h(metric.getDelta()) %></small>
                    </article>
                <% }} %>
            </div>
        </section>
        <section class="admin-section">
            <div class="section-head">
                <div>
                    <h2>최근 게시글</h2>
                    <p>최근 공개 게시글을 확인하고 필요한 경우 게시글 관리로 이동합니다.</p>
                </div>
                <a class="btn btn-secondary" href="<%= request.getContextPath() %>/admin/posts">게시글 관리</a>
            </div>
            <div class="admin-list">
                <% if (recentPostsLoadError) { %>
                    <div class="table-note" role="alert">최근 게시글을 불러오지 못했습니다.</div>
                <% } else if (recentPosts != null && !recentPosts.isEmpty()) {
                    for (PostDTO post : recentPosts) { %>
                        <a class="admin-list-row" href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>">
                            <strong><%= h(post.getTitle()) %></strong>
                            <span><%= h(post.getAuthorNickname()) %> / 조회 <%= post.getViewCount() %></span>
                        </a>
                <%  }
                   } else { %>
                    <div class="table-note">최근 게시글이 없습니다.</div>
                <% } %>
            </div>
        </section>
    </main>
</div>
<script src="<%= request.getContextPath() %>/assets/js/common.js"></script>
</body>
</html>
