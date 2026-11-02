<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, java.net.URLEncoder, com.scuredweb.common.util.HtmlUtil, com.scuredweb.common.security.CsrfUtil, com.scuredweb.user.dto.UserDTO, com.scuredweb.social.dto.PublicProfileDTO, com.scuredweb.social.dto.MessageDTO" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }
%>
<%
    PublicProfileDTO targetUser = (PublicProfileDTO) request.getAttribute("targetUser");
    List<MessageDTO> messages = (List<MessageDTO>) request.getAttribute("messages");
    Boolean composeMode = (Boolean) request.getAttribute("composeMode");
    String replyTitle = (String) request.getAttribute("replyTitle");
    boolean messagesLoadError = Boolean.TRUE.equals(request.getAttribute("messagesLoadError"));
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
    <title>쪽지 | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/user.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="container message-shell">
    <% if (errorMessage != null) { %><div class="alert error"><%= h(errorMessage) %></div><% } %>
    <% if (flashMessage != null) { %><div class="alert success"><%= h(flashMessage) %></div><% } %>
    <% if (Boolean.TRUE.equals(composeMode) && targetUser != null) { %>
    <section class="section-card">
        <h2><%= h(targetUser.getNickname()) %>님에게 쪽지 보내기</h2>
        <form class="stack-form" method="post" action="<%= request.getContextPath() %>/messages/send">
            <%= CsrfUtil.hiddenInput(request) %>
            <input type="hidden" name="receiver" value="<%= a(targetUser.getNickname()) %>">
            <label>제목<input type="text" name="title" maxlength="200" value="<%= a(replyTitle != null ? replyTitle : "") %>" placeholder="쪽지 제목을 입력하세요."></label>
            <label>내용<textarea name="content" rows="8" maxlength="2000" placeholder="전달할 내용을 입력하세요." required></textarea></label>
            <button type="submit" class="btn btn-primary">쪽지 보내기</button>
        </form>
    </section>
    <% } %>
    <section class="section-card">
        <div class="message-header-row">
            <h2>받은 쪽지</h2>
            <div class="inline-actions">
                <% if (messages != null && !messages.isEmpty()) { %>
                    <form method="post" action="<%= request.getContextPath() %>/messages/clear-read">
                        <%= CsrfUtil.hiddenInput(request) %>
                        <button type="submit" class="btn btn-secondary">읽은 쪽지 정리</button>
                    </form>
                <% } %>
                <a class="btn btn-secondary" href="<%= request.getContextPath() %>/mypage">마이페이지</a>
            </div>
        </div>
        <div class="message-list">
            <% if (messagesLoadError) { %>
                    <div class="line-card" role="alert"><strong>쪽지 목록을 불러오지 못했습니다.</strong></div>
            <% } else if (messages != null && !messages.isEmpty()) {
                for (MessageDTO message : messages) { %>
                    <div class="line-card">
                        <strong><%= h(message.getTitle()) %></strong>
                        <span>보낸 사람: <%= h(message.getSenderNickname()) %></span>
                        <p><%= h(message.getContent()) %></p>
                        <div class="inline-actions">
                            <a class="btn btn-secondary" href="<%= request.getContextPath() %>/messages/compose?to=<%= URLEncoder.encode(message.getSenderNickname(), "UTF-8") %>&replyTitle=<%= URLEncoder.encode("RE: " + message.getTitle(), "UTF-8") %>">답장</a>
                            <form method="post" action="<%= request.getContextPath() %>/messages/delete">
                                <%= CsrfUtil.hiddenInput(request) %>
                                <input type="hidden" name="messageId" value="<%= message.getMessageId() %>">
                                <button type="submit" class="btn btn-secondary">삭제</button>
                            </form>
                        </div>
                    </div>
            <%  }
               } else { %>
                    <div class="line-card"><strong>받은 쪽지가 없습니다.</strong></div>
            <% } %>
        </div>
    </section>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
