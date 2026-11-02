<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, com.scuredweb.board.dto.BoardDTO, com.scuredweb.board.dto.TagDTO, com.scuredweb.common.util.HtmlUtil" %>
<%!
    private String h(Object value) { return HtmlUtil.escapeHtml(value == null ? null : String.valueOf(value)); }
    private String u(Object value) { return HtmlUtil.escapeUrlParam(value == null ? null : String.valueOf(value)); }
%>
<%
    List<BoardDTO> boards = (List<BoardDTO>) request.getAttribute("boards");
    List<TagDTO> popularTags = (List<TagDTO>) request.getAttribute("popularTags");
%>
<aside class="side-panel">
    <section class="side-card">
        <h3>카테고리</h3>
        <div class="chip-list">
            <% if (boards != null) {
                for (BoardDTO board : boards) { %>
                    <a class="chip" href="<%= request.getContextPath() %>/board/list?boardCode=<%= u(board.getBoardCode()) %>"><%= h(board.getBoardName()) %></a>
            <%  }
               } %>
        </div>
    </section>
    <section class="side-card">
        <h3>인기 태그</h3>
        <div class="chip-list">
            <% if (popularTags != null) {
                for (TagDTO tag : popularTags) { %>
                    <span class="chip muted">#<%= h(tag.getTagName()) %></span>
            <%  }
               } %>
        </div>
    </section>
</aside>

