<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<footer class="site-footer">
    <div class="container footer-grid">
        <div>
            <h3>REDWEB</h3>
            <p>웹 해킹, CTF 질문, 보안 뉴스, 프로젝트 로그를 함께 나누는 다크 보안 커뮤니티입니다.</p>
            <p>(c) 2026 REDWEB</p>
        </div>
        <div>
            <h4>게시판</h4>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=free">자유게시판</a>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=ctf-qna">CTF Q&amp;A</a>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=owasp-top10">OWASP Top 10</a>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=faq">FAQ</a>
        </div>
        <div>
            <h4>정책</h4>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=faq">이용 안내</a>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=faq">개인정보 처리방침</a>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=faq">운영 정책</a>
            <a href="<%= request.getContextPath() %>/board/list?boardCode=faq">문의</a>
        </div>
    </div>
</footer>
<div id="userContextMenu" class="user-context-menu" hidden>
    <a id="contextProfileLink" href="#">프로필</a>
    <a id="contextMessageLink" href="#">쪽지</a>
    <a id="contextReportLink" href="#">신고</a>
</div>
<script src="<%= request.getContextPath() %>/assets/js/common.js"></script>
