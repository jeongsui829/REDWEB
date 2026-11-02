package com.redweb.board.dto;

import com.redweb.file.dto.AttachmentDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PostDTO {
    private Long postId;
    private Long userId;
    private String boardName;
    private String categoryName;
    private String title;
    private String content;
    private String authorNickname;
    private String status;
    private String sessionOwnerId;
    private int baseViewCount;
    private int viewCount;
    private int baseLikeCount;
    private int likeCount;
    private int baseDislikeCount;
    private int dislikeCount;
    private int commentCount;
    private boolean notice;
    private boolean pinned;
    private LocalDateTime createdAt;
    private List<TagDTO> tags = new ArrayList<>();
    private List<AttachmentDTO> attachments = new ArrayList<>();

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBoardName() { return boardName; }
    public void setBoardName(String boardName) { this.boardName = boardName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAuthorNickname() { return authorNickname; }
    public void setAuthorNickname(String authorNickname) { this.authorNickname = authorNickname; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSessionOwnerId() { return sessionOwnerId; }
    public void setSessionOwnerId(String sessionOwnerId) { this.sessionOwnerId = sessionOwnerId; }
    public int getBaseViewCount() { return baseViewCount; }
    public void setBaseViewCount(int baseViewCount) { this.baseViewCount = baseViewCount; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    public int getBaseLikeCount() { return baseLikeCount; }
    public void setBaseLikeCount(int baseLikeCount) { this.baseLikeCount = baseLikeCount; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public int getBaseDislikeCount() { return baseDislikeCount; }
    public void setBaseDislikeCount(int baseDislikeCount) { this.baseDislikeCount = baseDislikeCount; }
    public int getDislikeCount() { return dislikeCount; }
    public void setDislikeCount(int dislikeCount) { this.dislikeCount = dislikeCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public boolean isNotice() { return notice; }
    public void setNotice(boolean notice) { this.notice = notice; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<TagDTO> getTags() { return tags; }
    public void setTags(List<TagDTO> tags) { this.tags = tags; }
    public List<AttachmentDTO> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentDTO> attachments) { this.attachments = attachments; }
}
