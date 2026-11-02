<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.user.dto.UserDTO, com.scuredweb.common.constant.RoleCode, com.scuredweb.notification.dto.NotificationDTO, com.scuredweb.social.service.SocialService, com.scuredweb.common.exception.DataAccessException, com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
%>
<%
    UserDTO loginUser = (UserDTO) session.getAttribute("loginUser");
    boolean isAdmin = loginUser != null
            && (RoleCode.ADMIN.equals(loginUser.getRoleCode()) || RoleCode.SUPER_ADMIN.equals(loginUser.getRoleCode()));
    List<NotificationDTO> headerNotifications = java.util.List.of();
    long unreadNotificationCount = 0L;
    long unreadMessageCount = 0L;
    boolean headerSocialLoadError = false;
    if (loginUser != null) {
        try {
            headerNotifications = SocialService.getNotifications(loginUser.getUserId());
            unreadNotificationCount = SocialService.getUnreadNotificationCount(loginUser.getUserId());
            unreadMessageCount = SocialService.getUnreadMessageCount(loginUser.getUserId());
        } catch (DataAccessException exception) {
            headerSocialLoadError = true;
        }
    }
    String notificationBadgeText = unreadNotificationCount > 9 ? "9+" : String.valueOf(unreadNotificationCount);
    String messageBadgeText = unreadMessageCount > 9 ? "9+" : String.valueOf(unreadMessageCount);
%>
<header class="site-header"
        data-context-path="<%= request.getContextPath() %>"
        data-csrf-token="<%= a(request.getAttribute("csrfToken")) %>"
        data-csrf-parameter="<%= a(request.getAttribute("csrfParameterName")) %>"
        data-logged-in="<%= loginUser != null %>"
        data-admin="<%= isAdmin %>"
        data-nickname="<%= a(loginUser != null && loginUser.getNickname() != null ? loginUser.getNickname() : "") %>">
    <div class="container header-inner">
        <a class="brand brand-combined" href="<%= request.getContextPath() %>/home" aria-label="REDWEB 홈">
            <span class="brand-lockup">REDWEB</span>
        </a>

        <nav class="main-nav" aria-label="주요 메뉴">
            <a class="nav-single" href="<%= request.getContextPath() %>/board/list?boardCode=free">Forum</a>
            <a class="nav-single" href="<%= request.getContextPath() %>/board/list?boardCode=owasp-top10">Research</a>
            <a class="nav-single" href="<%= request.getContextPath() %>/board/list?boardCode=faq">FAQ</a>
        </nav>

        <form class="header-search-inline" action="<%= request.getContextPath() %>/search" method="get" role="search">
            <span class="icon-search" aria-hidden="true"></span>
            <input type="text" name="keyword" placeholder="검색..." aria-label="검색어">
            <kbd aria-hidden="true">/</kbd>
        </form>

        <div class="header-tools">
            <% if (loginUser != null) { %>
                <div class="header-bell-shell" data-read-url="<%= request.getContextPath() %>/notifications/read">
                    <button type="button" class="icon-btn bell-toggle" aria-label="알림 열기" aria-expanded="false" aria-controls="headerNotificationDrawer">
                        <span class="icon-bell" aria-hidden="true"></span>
                        <% if (unreadNotificationCount > 0) { %>
                            <span class="icon-badge"><%= notificationBadgeText %></span>
                        <% } %>
                    </button>
                    <div class="notification-drawer" id="headerNotificationDrawer" aria-label="최근 알림">
                        <div class="notification-drawer-header">
                            <strong>알림</strong>
                            <a href="<%= request.getContextPath() %>/mypage/notifications">전체 보기</a>
                        </div>
                        <div class="notification-drawer-list">
                            <% if (headerSocialLoadError) { %>
                                    <div class="notification-empty" role="alert">알림을 불러오지 못했습니다.</div>
                            <% } else if (!headerNotifications.isEmpty()) {
                                for (NotificationDTO notification : headerNotifications) {
                                    String targetUrl = notification.getTargetUrl() != null ? notification.getTargetUrl() : "/mypage/notifications";
                            %>
                                    <a class="notification-item" href="<%= request.getContextPath() + a(targetUrl) %>">
                                        <strong><%= h(notification.getTitle()) %></strong>
                                        <span><%= h(notification.getMessage()) %></span>
                                    </a>
                            <%  }
                               } else { %>
                                    <div class="notification-empty">새 알림이 없습니다.</div>
                            <% } %>
                        </div>
                    </div>
                </div>
                <span class="badge header-user-badge"><%= isAdmin ? "ADMIN" : "ME" %></span>
                <% if (isAdmin) { %>
                    <a class="text-link" href="<%= request.getContextPath() %>/admin/dashboard">관리자</a>
                <% } %>
                <a class="text-link message-link-shell" href="<%= request.getContextPath() %>/messages">
                    쪽지
                    <% if (unreadMessageCount > 0) { %>
                        <span class="message-badge"><%= messageBadgeText %></span>
                    <% } %>
                </a>
                <a class="text-link" href="<%= request.getContextPath() %>/mypage">내 정보</a>
                <form method="post" action="<%= request.getContextPath() %>/logout" class="inline-form">
                    <%= CsrfUtil.hiddenInput(request) %>
                    <button class="btn btn-primary" type="submit">로그아웃</button>
                </form>
            <% } else { %>
                <a class="text-link" href="<%= request.getContextPath() %>/login">로그인</a>
                <% if (Boolean.TRUE.equals(application.getAttribute("publicRegistrationEnabled"))) { %><a class="btn btn-primary" href="<%= request.getContextPath() %>/register">회원가입</a><% } %>
            <% } %>
        </div>
    </div>
</header>
