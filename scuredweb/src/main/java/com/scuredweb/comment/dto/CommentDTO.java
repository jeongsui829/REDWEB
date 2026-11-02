package com.scuredweb.comment.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommentDTO {
    private boolean deleted;
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    private Long commentId;
    private Long userId;
    private Long parentCommentId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;
    private List<CommentDTO> children = new ArrayList<>();

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }
    public String getAuthorNickname() { return authorNickname; }
    public void setAuthorNickname(String authorNickname) { this.authorNickname = authorNickname; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** Existing reply CSS is reused for every descendant; IDs/parent links remain unchanged. */
    public List<CommentDTO> getDescendants() {
        List<CommentDTO> result = new ArrayList<>();
        java.util.ArrayDeque<CommentDTO> pending = new java.util.ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        if (children != null) for (int i = children.size() - 1; i >= 0; i--) pending.push(children.get(i));
        while (!pending.isEmpty()) {
            CommentDTO next = pending.pop();
            if (!seen.add(next.getCommentId())) continue;
            result.add(next);
            if (next.children != null) for (int i = next.children.size() - 1; i >= 0; i--) pending.push(next.children.get(i));
        }
        return result;
    }

    public List<CommentDTO> getChildren() { return children; }
    public void setChildren(List<CommentDTO> children) { this.children = children; }
}

