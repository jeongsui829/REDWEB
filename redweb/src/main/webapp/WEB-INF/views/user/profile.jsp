<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.user.dto.UserDTO, com.redweb.social.dto.GuestbookEntryDTO" %>
<%
    UserDTO profileUser = (UserDTO) request.getAttribute("profileUser");
    UserDTO loginUser = (UserDTO) request.getAttribute("loginUser");
    Boolean isOwner = (Boolean) request.getAttribute("isOwner");
    List<GuestbookEntryDTO> guestbookEntries = (List<GuestbookEntryDTO>) request.getAttribute("guestbookEntries");
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
    <title><%= profileUser != null ? profileUser.getNickname() : "프로필" %> | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/user.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="container profile-view-shell">
    <% if (errorMessage != null) { %><div class="alert error"><%= errorMessage %></div><% } %>
    <% if (flashMessage != null) { %><div class="alert success"><%= flashMessage %></div><% } %>
    <section class="section-card profile-view-card">
        <div class="profile-hero">
            <div class="avatar">
                <% if (profileUser != null && "/assets/images/profile-default.png".equals(profileUser.getProfileImagePath())) { %>
                    <img src="<%= request.getContextPath() %>/assets/images/profile-default.png" alt="프로필 이미지">
                <% } else if (profileUser != null && profileUser.getProfileImagePath() != null && !profileUser.getProfileImagePath().isBlank()) { %>
                    <img src="<%= profileUser.getProfileImagePath() %>" alt="프로필 이미지">
                <% } else { %><img src="<%= request.getContextPath() %>/assets/images/profile-default.png" alt="기본 프로필 이미지"><% } %>
            </div>
            <div>
                <h1><%= profileUser != null ? profileUser.getNickname() : "프로필" %></h1>
                <p><%= profileUser != null ? profileUser.getEmail() : "" %></p>
                <p class="profile-bio"><%= profileUser != null && profileUser.getBio() != null ? profileUser.getBio() : "아직 자기소개가 작성되지 않았습니다." %></p>
                <div class="hero-actions">
                    <% if (Boolean.TRUE.equals(isOwner)) { %>
                        <a class="btn btn-primary" href="<%= request.getContextPath() %>/mypage/profile">프로필 관리</a>
                    <% } else if (loginUser != null) { %>
                        <a class="btn btn-primary" href="<%= request.getContextPath() %>/messages/compose?to=<%= profileUser.getNickname() %>">쪽지 보내기</a>
                    <% } %>
                </div>
            </div>
        </div>
    </section>
    <section class="section-card">
        <h2>방명록</h2>
        <% if (loginUser != null && !Boolean.TRUE.equals(isOwner)) { %>
            <form class="stack-form guestbook-form" method="post" action="<%= request.getContextPath() %>/profile/guestbook">
                <input type="hidden" name="nickname" value="<%= profileUser != null ? profileUser.getNickname() : "" %>">
                <label>방명록 메시지
                    <textarea name="content" rows="4" placeholder="이 프로필에 짧은 메시지를 남겨보세요." required></textarea>
                </label>
                <button type="submit" class="btn btn-primary">방명록 등록</button>
            </form>
        <% } %>
        <div class="guestbook-list">
            <% if (guestbookEntries != null && !guestbookEntries.isEmpty()) {
                for (GuestbookEntryDTO entry : guestbookEntries) { %>
                    <div class="line-card">
                        <strong><%= entry.getWriterNickname() %></strong>
                        <p><%= entry.getContent() %></p>
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
