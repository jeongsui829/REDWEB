<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, jakarta.servlet.http.HttpServletRequest, com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil, com.scuredweb.user.dto.UserDTO, com.scuredweb.social.dto.PublicProfileDTO, com.scuredweb.social.dto.GuestbookEntryDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
    private String u(Object value) { return HtmlUtil.escapeUrlParam(value == null ? null : String.valueOf(value)); }
    private String imageSrc(HttpServletRequest request, String path) {
        if (path == null || path.isBlank()) return request.getContextPath() + "/assets/images/profile-default.png";
        if ("/assets/images/profile-default.png".equals(path)) return request.getContextPath() + path;
        if (path.startsWith("/profile/image?savedName=")) return request.getContextPath() + path;
        return request.getContextPath() + "/assets/images/profile-default.png";
    }
%>
<%
    PublicProfileDTO profileUser = (PublicProfileDTO) request.getAttribute("profileUser");
    UserDTO loginUser = (UserDTO) request.getAttribute("loginUser");
    Boolean isOwner = (Boolean) request.getAttribute("isOwner");
    List<GuestbookEntryDTO> guestbookEntries = (List<GuestbookEntryDTO>) request.getAttribute("guestbookEntries");
    boolean guestbookLoadError = Boolean.TRUE.equals(request.getAttribute("guestbookLoadError"));
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
    <title><%= h(profileUser != null ? profileUser.getNickname() : "프로필") %> | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/user.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="container profile-view-shell">
    <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
    <% if (flashMessage != null) { %><div class="alert success"><%= h(flashMessage) %></div><% } %>
    <section class="section-card profile-view-card">
        <div class="profile-hero">
            <div class="avatar">
                <% if (profileUser != null && profileUser.getProfileImagePath() != null && !profileUser.getProfileImagePath().isBlank()) { %>
                    <img src="<%= a(imageSrc(request, profileUser.getProfileImagePath())) %>" alt="프로필 이미지">
                <% } else { %>S<% } %>
            </div>
            <div>
                <h1><%= h(profileUser != null ? profileUser.getNickname() : "프로필") %></h1>
                <p class="profile-bio"><%= h(profileUser != null && profileUser.getBio() != null ? profileUser.getBio() : "아직 자기소개가 작성되지 않았습니다.") %></p>
                <div class="hero-actions">
                    <% if (Boolean.TRUE.equals(isOwner)) { %>
                        <a class="btn btn-primary" href="<%= request.getContextPath() %>/mypage/profile">프로필 관리</a>
                    <% } else if (loginUser != null && profileUser != null) { %>
                        <a class="btn btn-primary" href="<%= request.getContextPath() %>/messages/compose?to=<%= u(profileUser.getNickname()) %>">쪽지 보내기</a>
                    <% } %>
                </div>
            </div>
        </div>
    </section>
    <section class="section-card">
        <h2>방명록</h2>
        <% if (loginUser != null && !Boolean.TRUE.equals(isOwner) && profileUser != null) { %>
            <form class="stack-form guestbook-form" method="post" action="<%= request.getContextPath() %>/profile/guestbook">
                <%= CsrfUtil.hiddenInput(request) %>
                <input type="hidden" name="nickname" value="<%= a(profileUser.getNickname()) %>">
                <label>방명록 메시지
                    <textarea name="content" rows="4" maxlength="2000" placeholder="이 프로필에 짧은 메시지를 남겨보세요." required></textarea>
                </label>
                <button type="submit" class="btn btn-primary">방명록 등록</button>
            </form>
        <% } %>
        <div class="guestbook-list">
            <% if (guestbookLoadError) { %>
                    <div class="line-card" role="alert"><strong>방명록을 불러오지 못했습니다.</strong></div>
            <% } else if (guestbookEntries != null && !guestbookEntries.isEmpty()) {
                for (GuestbookEntryDTO entry : guestbookEntries) { %>
                    <div class="line-card">
                        <strong><%= h(entry.getWriterNickname()) %></strong>
                        <p><%= h(entry.getContent()) %></p>
                    </div>
            <%  }
               } else { %>
                    <div class="line-card"><strong>아직 방명록이 없습니다.</strong></div>
            <% } %>
        </div>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
