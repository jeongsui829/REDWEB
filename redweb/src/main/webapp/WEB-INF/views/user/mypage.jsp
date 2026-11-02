<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.redweb.user.dto.UserDTO, com.redweb.user.dto.LoginHistoryDTO, com.redweb.notification.dto.NotificationDTO, com.redweb.board.dto.PostDTO, com.redweb.comment.dto.CommentDTO" %>
<%
    UserDTO user = (UserDTO) request.getAttribute("user");
    List<LoginHistoryDTO> loginHistory = (List<LoginHistoryDTO>) request.getAttribute("loginHistory");
    List<NotificationDTO> notifications = (List<NotificationDTO>) request.getAttribute("notifications");
    List<PostDTO> myPosts = (List<PostDTO>) request.getAttribute("myPosts");
    List<CommentDTO> myComments = (List<CommentDTO>) request.getAttribute("myComments");
    String currentSection = (String) request.getAttribute("currentSection");
    if (currentSection == null) {
        currentSection = "overview";
    }
    String errorMessage = (String) session.getAttribute("errorMessage");
    String flashMessage = (String) session.getAttribute("flashMessage");
    if (errorMessage != null) {
        session.removeAttribute("errorMessage");
    }
    if (flashMessage != null) {
        session.removeAttribute("flashMessage");
    }
    Number postCount = (Number) request.getAttribute("postCount");
    Number commentCount = (Number) request.getAttribute("commentCount");
    Number unreadNotificationCount = (Number) request.getAttribute("unreadNotificationCount");
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
        <a href="<%= request.getContextPath() %>/profile?nickname=<%= user != null ? java.net.URLEncoder.encode(user.getNickname(), "UTF-8") : "" %>">공개 프로필</a>
        <a href="<%= request.getContextPath() %>/messages">쪽지</a>
        <a class="<%= "withdraw".equals(currentSection) ? "active" : "" %>" href="<%= request.getContextPath() %>/mypage/withdraw">회원 탈퇴</a>
    </aside>
    <section class="mypage-main">
        <% if (errorMessage != null) { %><div class="alert error"><%= errorMessage %></div><% } %>
        <% if (flashMessage != null) { %><div class="alert success"><%= flashMessage %></div><% } %>
        <section class="section-card profile-summary">
            <div class="avatar">
                <% if (user != null && "/assets/images/profile-default.png".equals(user.getProfileImagePath())) { %>
                    <img src="<%= request.getContextPath() %>/assets/images/profile-default.png" alt="프로필 이미지">
                <% } else if (user != null && user.getProfileImagePath() != null && !user.getProfileImagePath().isBlank()) { %>
                    <img src="<%= user.getProfileImagePath() %>" alt="프로필 이미지">
                <% } else { %><img src="<%= request.getContextPath() %>/assets/images/profile-default.png" alt="기본 프로필 이미지"><% } %>
            </div>
            <div>
                <h1><%= user != null ? user.getNickname() : "회원" %></h1>
                <p><%= user != null ? user.getEmail() : "" %> | <%= user != null && user.getPhone() != null ? user.getPhone() : "-" %></p>
                <span class="badge"><%= user != null ? user.getRoleCode() : "MEMBER" %></span>
                <p class="profile-bio"><%= user != null && user.getBio() != null ? user.getBio() : "아직 자기소개가 작성되지 않았습니다." %></p>
            </div>
        </section>
        <% if ("profile".equals(currentSection)) { %>
            <section class="section-card">
                <h2>프로필 관리</h2>
                <form class="stack-form" method="post" action="<%= request.getContextPath() %>/mypage/profile" enctype="multipart/form-data">
                    <label>닉네임<input type="text" name="nickname" value="<%= user != null ? user.getNickname() : "" %>"></label>
                    <label>이메일<input type="email" name="email" value="<%= user != null ? user.getEmail() : "" %>"></label>
                    <label>전화번호<input type="text" name="phone" value="<%= user != null && user.getPhone() != null ? user.getPhone() : "" %>"></label>
                    <label>자기소개<textarea name="bio" rows="5"><%= user != null && user.getBio() != null ? user.getBio() : "" %></textarea></label>
                    <label>프로필 이미지<input type="file" name="profileImage" accept="image/*"></label>
                    <button type="submit" class="btn btn-primary">프로필 저장</button>
                </form>
            </section>
        <% } else if ("password".equals(currentSection)) { %>
            <section class="section-card">
                <h2>비밀번호 변경</h2>
                <form class="stack-form" method="post" action="<%= request.getContextPath() %>/mypage/password">
                    <label>현재 비밀번호<input type="password" name="currentPassword" required></label>
                    <label>새 비밀번호<input type="password" name="newPassword" required></label>
                    <label>새 비밀번호 확인<input type="password" name="confirmPassword" required></label>
                    <button type="submit" class="btn btn-primary">비밀번호 변경</button>
                </form>
            </section>
        <% } else if ("login-history".equals(currentSection)) { %>
            <section class="section-card">
                <h2>로그인 기록</h2>
                <% if (loginHistory != null && !loginHistory.isEmpty()) { for (LoginHistoryDTO history : loginHistory) { %>
                    <div class="line-card">
                        <strong><%= history.getLoginResult() %></strong>
                        <span>IP: <%= history.getLoginIp() %></span>
                        <span>환경: <%= history.getUserAgent() %></span>
                        <span><%= history.getLoginAt() %></span>
                    </div>
                <% }} else { %><div class="line-card"><strong>로그인 기록이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("notifications".equals(currentSection)) { %>
            <section class="section-card">
                <div class="message-header-row">
                    <h2>알림</h2>
                    <form method="post" action="<%= request.getContextPath() %>/notifications/clear-read">
                        <button type="submit" class="btn btn-secondary">읽은 알림 정리</button>
                    </form>
                </div>
                <% if (notifications != null && !notifications.isEmpty()) { for (NotificationDTO notification : notifications) { %>
                    <div class="line-card">
                        <strong><%= notification.getTitle() %></strong>
                        <p><%= notification.getMessage() %></p>
                        <form method="post" action="<%= request.getContextPath() %>/notifications/delete">
                            <input type="hidden" name="notificationId" value="<%= notification.getNotificationId() %>">
                            <button type="submit" class="btn btn-secondary">삭제</button>
                        </form>
                    </div>
                <% }} else { %><div class="line-card"><strong>알림이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("posts".equals(currentSection)) { %>
            <section class="section-card">
                <h2>내 게시글</h2>
                <% if (myPosts != null && !myPosts.isEmpty()) { for (PostDTO item : myPosts) { %>
                    <a class="line-card" href="<%= request.getContextPath() %>/board/detail?id=<%= item.getPostId() %>">
                        <strong><%= item.getTitle() %></strong>
                        <span><%= item.getBoardName() %> | 조회 <%= item.getViewCount() %> / 추천 <%= item.getLikeCount() %></span>
                    </a>
                <% }} else { %><div class="line-card"><strong>작성한 게시글이 없습니다.</strong></div><% } %>
            </section>
        <% } else if ("comments".equals(currentSection)) { %>
            <section class="section-card">
                <h2>내 댓글</h2>
                <% if (myComments != null && !myComments.isEmpty()) { for (CommentDTO item : myComments) { %>
                    <div class="line-card">
                        <strong><%= item.getAuthorNickname() %></strong>
                        <p><%= item.getContent() %></p>
                        <span><%= item.getCreatedAt() %></span>
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
                        <button type="submit" class="btn btn-secondary">회원 탈퇴 진행</button>
                    </form>
                </div>
            </section>
        <% } else if ("overview".equals(currentSection)) { %>
            <section class="section-card stat-grid">
                <div class="line-card"><span>게시글</span><strong><%= postCount != null ? postCount : 0 %></strong></div>
                <div class="line-card"><span>댓글</span><strong><%= commentCount != null ? commentCount : 0 %></strong></div>
                <div class="line-card"><span>읽지 않은 알림</span><strong><%= unreadNotificationCount != null ? unreadNotificationCount : 0 %></strong></div>
            </section>
            <section class="section-card">
                <h2>계정 개요</h2>
                <div class="line-card">
                    <strong>계정 정보</strong>
                    <span>아이디: <%= user != null ? user.getLoginId() : "" %></span>
                    <span>이름: <%= user != null ? user.getUserName() : "" %></span>
                    <span>상태: <%= user != null ? user.getStatus() : "" %></span>
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
