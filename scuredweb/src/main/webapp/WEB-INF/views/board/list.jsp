<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.board.dto.PostDTO, com.scuredweb.board.dto.BoardDTO, com.scuredweb.board.dto.TagDTO, com.scuredweb.common.util.HtmlUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String a(Object value) { return HtmlUtil.escapeAttribute(value == null ? null : String.valueOf(value)); }

    private String summarize(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?is)<img[^>]*>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
%>
<%
    List<PostDTO> posts = (List<PostDTO>) request.getAttribute("posts");
    List<PostDTO> popularPosts = (List<PostDTO>) request.getAttribute("popularPosts");
    List<BoardDTO> boards = (List<BoardDTO>) request.getAttribute("boards");
    List<TagDTO> popularTags = (List<TagDTO>) request.getAttribute("popularTags");
    BoardDTO currentBoard = (BoardDTO) request.getAttribute("currentBoard");
    String flashMessage = (String) request.getAttribute("flashMessage");
    String keyword = (String) request.getAttribute("keyword");
    String sort = (String) request.getAttribute("sort");
    boolean postsLoadError = Boolean.TRUE.equals(request.getAttribute("postsLoadError"));
    boolean popularPostsLoadError = Boolean.TRUE.equals(request.getAttribute("popularPostsLoadError"));
    boolean boardsLoadError = Boolean.TRUE.equals(request.getAttribute("boardsLoadError"));
    boolean popularTagsLoadError = Boolean.TRUE.equals(request.getAttribute("popularTagsLoadError"));
    String currentBoardCode = currentBoard != null ? currentBoard.getBoardCode() : "free";
    String currentBoardName = currentBoard != null ? currentBoard.getBoardName() : "Forum";
    boolean faqBoard = "faq".equals(currentBoardCode);
    int postCount = posts != null ? posts.size() : 0;
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="/WEB-INF/views/common/head.jspf" %>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><%= h(currentBoardName) %> | REDWEB</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/common.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/layout.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/components.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/pages/board.css">
</head>
<body>
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<main class="page-shell board-shell">
    <div class="container board-layout-grid board-layout-compact">
        <aside class="board-left-rail">
            <section class="board-shortcuts">
                <span class="rail-title">// 메뉴</span>
                <div class="shortcut-links primary-shortcuts">
                    <a class="<%= "free".equals(currentBoardCode) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=free"><span>Forum</span></a>
                    <a class="<%= "owasp-top10".equals(currentBoardCode) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=owasp-top10"><span>Research</span></a>
                </div>
            </section>
            <section class="board-shortcuts">
                <span class="rail-title">// 게시판</span>
                <div class="shortcut-links">
                    <% if (boardsLoadError) { %>
                            <span class="rail-empty" role="alert">게시판 정보를 불러오지 못했습니다.</span>
                    <% } else if (boards != null && !boards.isEmpty()) {
                        for (BoardDTO board : boards) { %>
                            <a class="<%= board.getBoardCode().equals(currentBoardCode) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=<%= a(board.getBoardCode()) %>">
                                <span><%= h(board.getBoardName()) %></span>
                            </a>
                    <%  }
                       } else { %>
                            <span class="rail-empty" role="status">표시할 게시판이 없습니다.</span>
                    <% } %>
                </div>
            </section>
            <section class="board-shortcuts">
                <span class="rail-title">// 인기 태그</span>
                <div class="chip-list">
                    <% if (popularTagsLoadError) { %>
                        <span class="rail-empty" role="alert">인기 태그를 불러오지 못했습니다.</span>
                    <% } else if (popularTags != null && !popularTags.isEmpty()) {
                        int tagLimit = Math.min(popularTags.size(), 10);
                        for (int i = 0; i < tagLimit; i++) {
                            TagDTO tag = popularTags.get(i);
                    %>
                        <span class="chip"># <%= h(tag.getTagName()) %></span>
                    <%  }
                       } else { %>
                        <span class="rail-empty" role="status">표시할 인기 태그가 없습니다.</span>
                    <% } %>
                </div>
            </section>
        </aside>

        <section class="board-center-rail">
            <button class="board-menu-toggle" type="button"
                    aria-expanded="false" aria-controls="mobileBoardMenu"
                    data-board-menu-toggle>
                게시판 메뉴
            </button>
            <nav class="mobile-board-menu" id="mobileBoardMenu" aria-label="게시판 이동" hidden>
                <a class="<%= "free".equals(currentBoardCode) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=free">Forum</a>
                <a class="<%= "owasp-top10".equals(currentBoardCode) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=owasp-top10">Research</a>
                <% if (boards != null) {
                    for (BoardDTO board : boards) { %>
                        <a class="<%= board.getBoardCode().equals(currentBoardCode) ? "active" : "" %>"
                           href="<%= request.getContextPath() %>/board/list?boardCode=<%= a(board.getBoardCode()) %>"><%= h(board.getBoardName()) %></a>
                <%  }
                   } %>
            </nav>
            <section class="board-page-head">
                <span class="live-pill">REDWEB BOARD</span>
                <h1><%= h(currentBoardName) %></h1>
                <p><%= h(currentBoard != null ? currentBoard.getDescription() : "게시글 목록을 확인하고 새 글을 작성할 수 있습니다.") %></p>
                <% if (flashMessage != null) { %>
                    <div class="alert success"><%= h(flashMessage) %></div>
                <% } %>
            </section>

            <% if (!faqBoard) { %>
                <section class="board-toolbar">
                    <div class="board-filter-tabs">
                        <a class="<%= sort == null || sort.isBlank() || "latest".equals(sort) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=<%= a(currentBoardCode) %>">전체</a>
                        <a class="<%= "likes".equals(sort) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=<%= a(currentBoardCode) %>&sort=likes">인기</a>
                        <a class="<%= "views".equals(sort) ? "active" : "" %>" href="<%= request.getContextPath() %>/board/list?boardCode=<%= a(currentBoardCode) %>&sort=views">조회</a>
                        <a href="<%= request.getContextPath() %>/board/list?boardCode=ctf-qna">질문</a>
                    </div>
                    <form class="toolbar-grid" method="get" action="<%= request.getContextPath() %>/board/list">
                        <input type="hidden" name="boardCode" value="<%= a(currentBoardCode) %>">
                        <input type="text" name="keyword" value="<%= a(keyword != null ? keyword : "") %>" placeholder="제목 검색">
                        <select name="sort" aria-label="게시글 정렬">
                            <option value="latest" <%= "latest".equals(sort) ? "selected" : "" %>>최신순</option>
                            <option value="views" <%= "views".equals(sort) ? "selected" : "" %>>조회순</option>
                            <option value="likes" <%= "likes".equals(sort) ? "selected" : "" %>>추천순</option>
                        </select>
                        <button class="btn btn-secondary" type="submit">검색</button>
                    </form>
                </section>
            <% } %>

            <% if (faqBoard) { %>
                <section class="faq-card">
                    <p class="faq-question">&gt; REDWEB은 어떤 곳인가요?</p>
                    <p class="faq-answer">보안 학습자와 연구자가 게시글, CTF 질문, 프로젝트 공유, 운영 공지를 함께 나누는 커뮤니티입니다.</p>
                    <p class="faq-question">&gt; 어떤 글이 금지되나요?</p>
                    <p class="faq-answer">불법 공격 가이드, 악성코드 배포, 개인정보 유출, 계정 정보 공유는 금지됩니다.</p>
                </section>
            <% } else { %>
                <div class="feed-meta">
                    <span><%= postCount %>개의 글</span>
                    <a class="btn btn-primary write-button inline-write-button" href="<%= request.getContextPath() %>/board/write">글쓰기</a>
                </div>
                <section class="board-list-card">
                    <% if (postsLoadError) { %>
                        <div class="board-row empty-row" role="alert">
                            <div class="board-row-main">
                                <div class="title-line"><strong>게시글을 불러오지 못했습니다.</strong></div>
                                <p class="board-row-summary">잠시 후 다시 시도해 주세요.</p>
                            </div>
                        </div>
                    <% } else if (posts != null && !posts.isEmpty()) {
                        for (PostDTO post : posts) {
                            String summary = summarize(post.getContent());
                    %>
                        <a class="board-row" href="<%= request.getContextPath() %>/board/detail?id=<%= post.getPostId() %>">
                            <div class="board-row-main">
                                <div class="post-kicker">
                                    <span>@<%= h(post.getAuthorNickname()) %></span>
                                    <% if (post.isNotice()) { %><strong>공지</strong><% } %>
                                    <em><%= h(post.getCategoryName() != null ? post.getCategoryName() : post.getBoardName()) %></em>
                                </div>
                                <div class="title-line">
                                    <strong><%= h(post.getTitle()) %></strong>
                                </div>
                                <% if (summary != null && !summary.isEmpty()) { %>
                                    <p class="board-row-summary"><%= h(summary) %></p>
                                <% } %>
                                <div class="board-row-meta">
                                    <span>조회 <%= post.getViewCount() %></span>
                                    <span>댓글 <%= post.getCommentCount() %></span>
                                    <span>추천 <%= post.getLikeCount() %></span>
                                </div>
                            </div>
                        </a>
                    <%  }
                       } else { %>
                        <div class="board-row empty-row">
                            <div class="board-row-main">
                                <div class="title-line"><strong>게시글이 없습니다.</strong></div>
                                <p class="board-row-summary">이 게시판의 첫 번째 글을 작성해 보세요.</p>
                            </div>
                        </div>
                    <% } %>
                </section>
            <% } %>
        </section>

        <aside class="board-right-rail">
            <section class="side-card online-users-card" data-online-widget>
                <h3>// 최근 2분 활성 세션</h3>
                <div class="online-summary" aria-live="polite">
                    <strong data-online-count>--</strong>
                    <span>전체 최근 2분 활성 세션</span>
                </div>
                <div class="online-session-stats" aria-label="접속 세션 구성">
                    <span>회원 <strong data-authenticated-session-count>--</strong></span>
                    <span>게스트 <strong data-guest-session-count>--</strong></span>
                </div>
                <div class="online-list" data-online-users aria-live="polite">
                    <span>로그인 회원 정보를 불러오는 중입니다.</span>
                </div>
                <p class="online-guest-summary" data-online-guest-summary>게스트 --세션</p>
                <p class="online-session-note">브라우저 종료 후에도 세션이 최대 2분간 표시될 수 있습니다.</p>
            </section>
            <section class="side-card">
                <h3>// 실시간 인기글</h3>
                <div class="side-list">
                    <% if (popularPostsLoadError) { %>
                        <div role="alert">
                            <span>!</span>
                            <strong>인기글을 불러오지 못했습니다.</strong>
                        </div>
                    <% } else if (popularPosts != null && !popularPosts.isEmpty()) {
                        int hotLimit = Math.min(popularPosts.size(), 5);
                        for (int i = 0; i < hotLimit; i++) {
                            PostDTO hotPost = popularPosts.get(i);
                    %>
                        <a href="<%= request.getContextPath() %>/board/detail?id=<%= hotPost.getPostId() %>">
                            <span><%= i + 1 %></span>
                            <strong><%= h(hotPost.getTitle()) %></strong>
                        </a>
                    <%  }
                       } else { %>
                        <div>
                            <span>0</span>
                            <strong>인기글이 없습니다.</strong>
                        </div>
                    <% } %>
                </div>
            </section>
        </aside>
    </div>
</main>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
</body>
</html>
