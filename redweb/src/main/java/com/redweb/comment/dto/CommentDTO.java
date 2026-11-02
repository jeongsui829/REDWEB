package com.redweb.comment.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommentDTO {
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
    public List<CommentDTO> getChildren() { return children; }
    public void setChildren(List<CommentDTO> children) { this.children = children; }
}
