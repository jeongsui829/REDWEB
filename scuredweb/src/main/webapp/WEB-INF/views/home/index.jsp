<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.board.dto.BoardDTO, com.scuredweb.board.dto.PostDTO, com.scuredweb.board.dto.TagDTO, com.scuredweb.common.util.HtmlUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
%>
<%
    List<PostDTO> latestPosts = (List<PostDTO>) request.getAttribute("latestPosts");
    List<PostDTO> trendingPosts = (List<PostDTO>) request.getAttribute("trendingPosts");
    List<BoardDTO> boards = (List<BoardDTO>) request.getAttribute("boards");
    List<TagDTO> popularTags = (List<TagDTO>) request.getAttribute("popularTags");
    boolean latestPostsLoadError = Boolean.TRUE.equals(request.getAttribute("latestPostsLoadError"));
    boolean trendingPostsLoadError = Boolean.TRUE.equals(request.getAttribute("trendingPostsLoadError"));
    boolean boardsLoadError = Boolean.TRUE.equals(request.getAttribute("boardsLoadError"));
    boolean popularTagsLoadError = Boolean.TRUE.equals(request.getAttribute("popularTagsLoadError"));
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/home.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<main class="landing-shell">
    <div class="container home-forum-layout">
        <aside class="home-left-rail">
            <section class="home-widget">
                <span class="rail-title">// 게시판</span>
                <div class="home-board-list">
                    <% if (boardsLoadError) { %>
                        <div class="home-empty-state" role="alert">게시판 정보를 불러오지 못했습니다.</div>
                    <% } else if (boards != null && !boards.isEmpty()) {
                        int boardLimit = Math.min(boards.size(), 8);
                        for (int i = 0; i < boardLimit; i++) {
                            BoardDTO board = boards.get(i);
                    %>
                        <a href="<%= request.getContextPath() %>/board/list?boardCode=<%= h(board.getBoardCode()) %>">
                            <span><%= h(board.getBoardName()) %></span>
                        </a>
                    <%  }
                       } else { %>
                        <div class="home-empty-state" role="status">표시할 게시판 정보가 없습니다.</div>
                    <% } %>
                </div>
            </section>

            <section class="home-widget">
                <span class="rail-title">// 인기 태그</span>
                <div class="home-tag-cloud">
                    <% if (popularTagsLoadError) { %>
                        <div class="home-empty-state" role="alert">인기 태그를 불러오지 못했습니다.</div>
                    <% } else if (popularTags != null && !popularTags.isEmpty()) {
                        int tagLimit = Math.min(popularTags.size(), 12);
                        for (int i = 0; i < tagLimit; i++) {
                            TagDTO tag = popularTags.get(i);
                    %>
                        <span># <%= h(tag.getTagName()) %></span>
                    <%  }
                       } else { %>
                        <div class="home-empty-state" role="status">표시할 인기 태그가 없습니다.</div>
                    <% } %>
                </div>
            </section>
        </aside>

        <section class="home-main-rail">
            <section class="landing-hero">
                <div class="hero-clock-bar">
                    <span>현재 시각</span>
                    <strong id="liveClock">--:--:--</strong>
                    <em id="liveDate">----</em>
                </div>

                <div class="landing-copy landing-copy-full">
                    <span class="eyebrow">보안 커뮤니티 플랫폼</span>
                    <h1>보안 학습과 프로젝트 기록을 함께 쌓아가는 REDWEB</h1>
                    <p>
                        REDWEB은 보안 공부 기록, CTF 질문, 프로젝트 공유, 취약점 지식 정리를 한곳에서 이어가는
                        Java/JSP 기반 커뮤니티입니다. 학습 흐름을 남기고 질문을 주고받으며 운영 안내까지 빠르게 확인할 수 있습니다.
                    </p>
                    <div class="hero-summary">
                        <strong>서비스 개요</strong>
                        <p>커뮤니티 게시판, 정보공유 게시판, FAQ, 마이페이지, 관리자 기능을 실제 서비스 형태로 묶은 보안 학습 플랫폼입니다.</p>
                    </div>
                    <div class="hero-actions">
                        <a class="btn btn-primary" href="<%= request.getContextPath() %>/board/list?boardCode=free">커뮤니티 둘러보기</a>
                        <% if (Boolean.TRUE.equals(application.getAttribute("publicRegistrationEnabled"))) { %><a class="btn btn-secondary" href="<%= request.getContextPath() %>/register">회원가입</a><% } %>
                    </div>
                </div>
            </section>

            <section class="trust-band">
                <div class="trust-card">
                    <span>커뮤니티</span>
                    <strong>자유게시판부터 프로젝트 공유까지 활동 중심 게시판 구성</strong>
                </div>
                <div class="trust-card">
                    <span>정보공유</span>
                    <strong>OWASP, 보안뉴스, Linux, Network, Web 기초를 주제별로 정리</strong>
                </div>
                <div class="trust-card">
                    <span>운영 구조</span>
                    <strong>FAQ, 마이페이지, 관리자 페이지까지 실제 서비스 흐름 유지</strong>
                </div>
            </section>

            <section class="category-section">
                <div class="section-intro">
                    <span class="eyebrow">카테고리 진입</span>
                    <h2>상단 메뉴와 사이드 게시판 목록을 통해 원하는 주제로 바로 이동할 수 있습니다.</h2>
                </div>
                <div class="category-grid category-grid-wide">
                    <article class="category-card category-group-card">
                        <span class="category-index">01</span>
                        <h3>커뮤니티</h3>
                        <p>활동 기록, 질문, 답변, 프로젝트 공유가 중심이 되는 공간입니다.</p>
                        <ul class="category-links">
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=free">자유게시판</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=daily-log">Daily 공부 로그</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=ctf-qna">CTF 질문</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=project-share">프로젝트 공유</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=qna">질문답변</a></li>
                        </ul>
                    </article>
                    <article class="category-card category-group-card">
                        <span class="category-index">02</span>
                        <h3>정보공유</h3>
                        <p>보안 기초 지식과 최신 이슈를 주제별로 정리하는 공간입니다.</p>
                        <ul class="category-links">
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=owasp-top10">OWASP Top 10</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=security-news">보안뉴스</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=hacking-basic">해킹 기초 지식</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=linux-basic">Linux 기초</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=network-basic">Network</a></li>
                            <li><a href="<%= request.getContextPath() %>/board/list?boardCode=web-basic">Web 기초</a></li>
                        </ul>
                    </article>
                    <a class="category-card" href="<%= request.getContextPath() %>/board/list?boardCode=faq">
                        <span class="category-index">03</span>
                        <h3>FAQ</h3>
                        <p>계정, 게시글, 파일 업로드, 운영 정책과 관련된 안내를 빠르게 확인합니다.</p>
                    </a>
                </div>
            </section>

            <section class="value-section">
                <div class="section-intro">
                    <span class="eyebrow">서비스 구성 방식</span>
                    <h2>처음 들어온 사용자도 어디로 이동해야 하는지 바로 알 수 있도록 메뉴를 나누었습니다.</h2>
                </div>
                <div class="value-grid">
                    <article class="value-card">
                        <h3>학습 흐름 기록</h3>
                        <p>Daily 공부 로그와 질문답변 게시판을 통해 학습 과정을 남기고 다시 찾아볼 수 있습니다.</p>
                    </article>
                    <article class="value-card">
                        <h3>보안 지식 아카이브</h3>
                        <p>OWASP Top 10, 보안뉴스, Linux, Network, Web 기초 게시판으로 주제별 탐색이 가능합니다.</p>
                    </article>
                    <article class="value-card">
                        <h3>운영과 관리 분리</h3>
                        <p>FAQ와 관리자 페이지를 분리해 사용자 안내와 운영 관리를 명확하게 나누었습니다.</p>
                    </article>
                </div>
            </section>
        </section>

        <aside class="home-right-rail">
            <section class="home-widget" data-online-widget>
                <span class="rail-title">// 최근 2분 활성 세션</span>
                <div class="home-online-count" aria-live="polite">
                    <strong data-online-count>--</strong>
                    <span>개 최근 2분 활성 세션</span>
                </div>
                <div class="online-session-stats" aria-label="접속 세션 구성">
                    <span>회원 <strong data-authenticated-session-count>--</strong></span>
                    <span>게스트 <strong data-guest-session-count>--</strong></span>
                </div>
                <div class="home-online-list" data-online-users aria-live="polite">
                    <span>로그인 회원 정보를 확인하고 있습니다.</span>
                </div>
                <p class="online-guest-summary" data-online-guest-summary>게스트 --세션</p>
                <p class="online-session-note">브라우저 종료 후에도 세션이 최대 2분간 표시될 수 있습니다.</p>
            </section>

            <section class="home-widget">
                <span class="rail-title">// 지금 인기 글</span>
                <div class="home-hot-list">
                    <% if (trendingPostsLoadError) { %>
                        <div class="home-empty-state" role="alert">인기 게시글을 불러오지 못했습니다.</div>
                    <% } else if (trendingPosts != null && !trendingPosts.isEmpty()) {
                        int hotLimit = Math.min(trendingPosts.size(), 5);
                        for (int i = 0; i < hotLimit; i++) {
                            PostDTO post = trendingPosts.get(i);
                    %>
                        <a href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>">
                            <b><%= i + 1 %></b>
                            <span><%= h(post.getTitle()) %></span>
                        </a>
                    <%  }
                       } else { %>
                        <div class="home-empty-state" role="status">표시할 인기 게시글이 없습니다.</div>
                    <% } %>
                </div>
            </section>

            <section class="home-widget">
                <span class="rail-title">// 최신 게시글</span>
                <div class="home-hot-list">
                    <% if (latestPostsLoadError) { %>
                        <div class="home-empty-state" role="alert">최신 게시글을 불러오지 못했습니다.</div>
                    <% } else if (latestPosts != null && !latestPosts.isEmpty()) {
                        int latestLimit = Math.min(latestPosts.size(), 4);
                        for (int i = 0; i < latestLimit; i++) {
                            PostDTO post = latestPosts.get(i);
                    %>
                        <a href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>">
                            <b>NEW</b>
                            <span><%= h(post.getTitle()) %></span>
                        </a>
                    <%  }
                       } else { %>
                        <div class="home-empty-state" role="status">표시할 최신 게시글이 없습니다.</div>
                    <% } %>
                </div>
            </section>
        </aside>
    </div>
</main>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
<script src="<%= request.getContextPath() %>/assets/js/home.js"></script>
</body>
</html>
