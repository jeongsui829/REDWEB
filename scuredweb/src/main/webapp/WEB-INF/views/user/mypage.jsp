<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, java.net.URLEncoder, jakarta.servlet.http.HttpServletRequest, com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil, com.scuredweb.user.dto.UserDTO, com.scuredweb.user.dto.LoginHistoryDTO, com.scuredweb.notification.dto.NotificationDTO, com.scuredweb.board.dto.PostDTO, com.scuredweb.comment.dto.CommentDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
    private String imageSrc(HttpServletRequest request, String path) {
        if (path == null || path.isBlank()) return request.getContextPath() + "/assets/images/profile-default.png";
        if ("/assets/images/profile-default.png".equals(path)) return request.getContextPath() + path;
        if (path.startsWith("/profile/image?savedName=")) return request.getContextPath() + path;
        return request.getContextPath() + "/assets/images/profile-default.png";
    }
%>
<%
    UserDTO user = (UserDTO) request.getAttribute("user");
    List<NotificationDTO> notifications = (List<NotificationDTO>) request.getAttribute("notifications");
    List<LoginHistoryDTO> loginHistory = (List<LoginHistoryDTO>) request.getAttribute("loginHistory");
    List<PostDTO> myPosts = (List<PostDTO>) request.getAttribute("myPosts");
    List<CommentDTO> myComments = (List<CommentDTO>) request.getAttribute("myComments");
    Integer postCount = (Integer) request.getAttribute("postCount");
    Integer commentCount = (Integer) request.getAttribute("commentCount");
    Long unreadNotificationCount = (Long) request.getAttribute("unreadNotificationCount");
    String currentSection = (String) request.getAttribute("currentSection");
    if (currentSection == null || currentSection.isBlank()) currentSection = "overview";
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
    <title>마이페이지 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/user.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="container mypage-shell">
    <aside class="mypage-nav">
        <a class="<%= "overview".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage">개요</a>
        <a class="<%= "profile".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/profile">프로필 관리</a>
        <a class="<%= "password".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/password">비밀번호 변경</a>
        <a class="<%= "login-history".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/login-history">로그인 기록</a>
        <a class="<%= "notifications".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/notifications">알림</a>
        <a class="<%= "posts".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/posts">내 게시글</a>
        <a class="<%= "comments".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/comments">내 댓글</a>
        <a href="<%= request.getContextPath() %>/profile?nickname=<%= user != null ? URLEncoder.encode(user.getNickname(), "UTF-8") : "" %>">공개 프로필</a>
        <a href="<%= request.getContextPath() %>/messages">쪽지</a>
        <a class="<%= "withdraw".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/withdraw">회원 탈퇴</a>
    </aside>
    <section class="mypage-main">
        <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
        <% if (flashMessage != null) { %><div class="alert success"><%= h(flashMessage) %></div><% } %>
        <section class="section-card profile-summary">
            <div class="avatar">
                <% if (user != null && user.getProfileImagePath() != null && !user.getProfileImagePath().isBlank()) { %>
                    <img src="<%= a(imageSrc(request, user.getProfileImagePath())) %>" alt="프로필 이미지">
                <% } else { %>S<% } %>
            </div>
            <div>
                <h1><%= h(user != null ? user.getNickname() : "회원") %></h1>
                <p><%= h(user != null ? user.getEmail() : "") %></p>
                <p class="profile-bio"><%= h(user != null && user.getBio() != null ? user.getBio() : "아직 자기소개가 작성되지 않았습니다.") %></p>
            </div>
        </section>
        <% if ("profile".equals(currentSection)) { %>
            <section class="section-card">
                <h2>프로필 관리</h2>
                <form class="stack-form" method="post" action="<%= request.getContextPath() %>/mypage/profile" enctype="multipart/form-data">
                    <%= CsrfUtil.hiddenInput(request) %>
                    <label>닉네임<input type="text" name="nickname" maxlength="100" value="<%= a(user != null ? user.getNickname() : "") %>" required></label>
                    <label>이메일 변경 시 현재 비밀번호<input type="password" name="currentPassword" maxlength="128" autocomplete="current-password"></label>
                    <label>이메일<input type="email" name="email" maxlength="150" value="<%= a(user != null ? user.getEmail() : "") %>" required></label>
                    <label>전화번호<input type="text" name="phone" maxlength="30" value="<%= a(user != null && user.getPhone() != null ? user.getPhone() : "") %>"></label>
                    <label>자기소개<textarea name="bio" rows="5" maxlength="1000"><%= h(user != null && user.getBio() != null ? user.getBio() : "") %></textarea></label>
                    <label>프로필 이미지<input type="file" name="profileImage" accept="image/*"></label>
                    <button type="submit" class="btn btn-primary">프로필 저장</button>
                </form>
            </section>
        <% } else if ("password".equals(currentSection)) { %>
            <section class="section-card">
                <h2>비밀번호 변경</h2>
                <form class="stack-form" method="post" action="<%= request.getContextPath() %>/mypage/password">
                    <%= CsrfUtil.hiddenInput(request) %>
                    <label>현재 비밀번호<input type="password" maxlength="128" name="currentPassword" required></label>
                    <label>새 비밀번호<input type="password" maxlength="128" name="newPassword" required></label>
                    <label>새 비밀번호 확인<input type="password" maxlength="128" name="confirmPassword" required></label>
                    <button type="submit" class="btn btn-primary">비밀번호 변경</button>
                </form>
            </section>
        <% } else if ("login-history".equals(currentSection)) { %>
            <section class="section-card">
                <h2>로그인 기록</h2>
                <% if (loginHistory != null && !loginHistory.isEmpty()) { for (LoginHistoryDTO history : loginHistory) { %>
                    <div class="line-card">
                        <strong><%= h(history.getLoginResult()) %></strong>
                        <span>IP: <%= h(history.getLoginIp()) %></span>
                        <span><%= history.getLoginAt() %></span>
                    </div>
                <% }} else { %><div class="line-card"><strong>로그인 기록이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("notifications".equals(currentSection)) { %>
            <section class="section-card">
                <div class="message-header-row">
                    <h2>알림</h2>
                    <form method="post" action="<%= request.getContextPath() %>/notifications/clear-read">
                        <%= CsrfUtil.hiddenInput(request) %>
                        <button type="submit" class="btn btn-secondary">읽은 알림 정리</button>
                    </form>
                </div>
                <% if (notifications != null && !notifications.isEmpty()) { for (NotificationDTO notification : notifications) { %>
                    <div class="line-card">
                        <strong><%= h(notification.getTitle()) %></strong>
                        <p><%= h(notification.getMessage()) %></p>
                        <form method="post" action="<%= request.getContextPath() %>/notifications/delete">
                            <%= CsrfUtil.hiddenInput(request) %>
                            <input type="hidden" name="notificationId" value="<%= notification.getNotificationId() %>">
                            <button type="submit" class="btn btn-secondary">삭제</button>
                        </form>
                    </div>
                <% }} else { %><div class="line-card"><strong>알림이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("posts".equals(currentSection)) { %>
            <section class="section-card">
                <h2>내 게시글</h2>
                <% if (myPosts != null && !myPosts.isEmpty()) { for (PostDTO post : myPosts) { %>
                    <a class="line-card" href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>">
                        <strong><%= h(post.getTitle()) %></strong>
                        <span>조회 <%= post.getViewCount() %> / 추천 <%= post.getLikeCount() %></span>
                    </a>
                <% }} else { %><div class="line-card"><strong>작성한 게시글이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("comments".equals(currentSection)) { %>
            <section class="section-card">
                <h2>내 댓글</h2>
                <% if (myComments != null && !myComments.isEmpty()) { for (CommentDTO comment : myComments) { %>
                    <div class="line-card">
                        <p><%= h(comment.getContent()) %></p>
                        <span><%= comment.getCreatedAt() %></span>
                    </div>
                <% }} else { %><div class="line-card"><strong>작성한 댓글이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("withdraw".equals(currentSection)) { %>
            <section class="section-card">
                <h2>회원 탈퇴</h2>
                <div class="line-card">
                    <strong>회원 탈퇴 확인</strong>
                    <p>탈퇴 후 현재 계정 데이터가 삭제되며 이 계정으로 다시 로그인할 수 없습니다.</p>
                    <form method="post" action="<%= request.getContextPath() %>/mypage/withdraw" data-confirm="정말로 회원 탈퇴를 진행하시겠습니까?">
                        <label>현재 비밀번호<input type="password" name="currentPassword" maxlength="128" autocomplete="current-password" required></label>
                        <%= CsrfUtil.hiddenInput(request) %>
                        <button type="submit" class="btn btn-secondary">회원 탈퇴 진행</button>
                    </form>
                </div>
            </section>
        <% } else { %>
            <section class="section-card stat-grid">
                <div class="line-card"><span>게시글</span><strong><%= postCount != null ? postCount : 0 %></strong></div>
                <div class="line-card"><span>댓글</span><strong><%= commentCount != null ? commentCount : 0 %></strong></div>
                <div class="line-card"><span>읽지 않은 알림</span><strong><%= unreadNotificationCount != null ? unreadNotificationCount : 0 %></strong></div>
            </section>
            <section class="section-card">
                <h2>계정 개요</h2>
                <div class="line-card">
                    <strong>계정 정보</strong>
                    <span>아이디: <%= h(user != null ? user.getLoginId() : "") %></span>
                    <span>가입일: <%= user != null ? user.getCreatedAt() : "" %></span>
                    <span>최근 로그인: <%= user != null ? user.getLastLoginAt() : "" %></span>
                </div>
            </section>
        <% } %>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
