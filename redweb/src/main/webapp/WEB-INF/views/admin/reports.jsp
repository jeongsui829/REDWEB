<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.social.dto.ReportDTO" %>
<%
    List<ReportDTO> reports = (List<ReportDTO>) request.getAttribute("reports");
    String flashMessage = (String) request.getAttribute("flashMessage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>신고 관리 | REDWEB</title>
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
                    <h2>신고 관리</h2>
                    <p>접수된 신고를 확인하고 제재 처리 또는 조치 없음으로 마감합니다.</p>
                </div>
            </div>
            <% if (flashMessage != null && !flashMessage.isBlank()) { %><div class="admin-alert"><%= flashMessage %></div><% } %>
            <div class="admin-table-wrap">
                <table class="admin-table">
                    <thead>
                    <tr>
                        <th>번호</th><th>신고자</th><th>대상</th><th>사유</th><th>상태</th><th>제재</th><th>접수 시각</th><th>처리</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% if (reports != null && !reports.isEmpty()) {
                        for (ReportDTO report : reports) {
                            boolean pending = "PENDING".equals(report.getStatus());
                            boolean resolved = "RESOLVED".equals(report.getStatus());
                    %>
                        <tr>
                            <td><%= report.getReportId() %></td>
                            <td><%= report.getReporterNickname() %></td>
                            <td><%= report.getTargetNickname() %></td>
                            <td><%= report.getReason() %></td>
                            <td><span class="status-badge <%= resolved ? "is-active" : (pending ? "is-suspended" : "") %>"><%= resolved ? "처리 완료" : (pending ? "대기 중" : "조치 없음") %></span></td>
                            <td><%= report.getPenaltySummary() != null ? report.getPenaltySummary() : "-" %></td>
                            <td><%= report.getCreatedAt() %></td>
                            <td>
                                <% if (pending) { %>
                                    <div class="admin-inline-actions">
                                        <form method="post" action="<%= request.getContextPath() %>/admin/reports/process" data-confirm="이 신고를 처리하고 대상 계정을 정지하시겠습니까?">
                                            <input type="hidden" name="reportId" value="<%= report.getReportId() %>">
                                            <button type="submit" class="btn btn-secondary">처리</button>
                                        </form>
                                        <form method="post" action="<%= request.getContextPath() %>/admin/reports/dismiss" data-confirm="이 신고를 조치 없음으로 정리하시겠습니까?">
                                            <input type="hidden" name="reportId" value="<%= report.getReportId() %>">
                                            <button type="submit" class="btn btn-light">조치 없음</button>
                                        </form>
                                    </div>
                                <% } else { %>
                                    <span class="table-note"><%= report.getProcessedByNickname() != null ? report.getProcessedByNickname() : "-" %> 처리</span>
                                <% } %>
                            </td>
                        </tr>
                    <%  }
                       } else { %>
                        <tr><td colspan="8">접수된 신고가 없습니다.</td></tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
<script src="<%= request.getContextPath() %>/assets/js/common.js"></script>
</body>
</html>
