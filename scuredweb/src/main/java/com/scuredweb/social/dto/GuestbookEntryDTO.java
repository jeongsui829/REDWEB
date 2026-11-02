package com.scuredweb.social.dto;

import java.time.LocalDateTime;

public class GuestbookEntryDTO {
    private Long entryId;
    private Long ownerUserId;
    private String ownerNickname;
    private Long writerUserId;
    private String writerNickname;
    private String content;
    private LocalDateTime createdAt;

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerNickname() { return ownerNickname; }
    public void setOwnerNickname(String ownerNickname) { this.ownerNickname = ownerNickname; }
    public Long getWriterUserId() { return writerUserId; }
    public void setWriterUserId(Long writerUserId) { this.writerUserId = writerUserId; }
    public String getWriterNickname() { return writerNickname; }
    public void setWriterNickname(String writerNickname) { this.writerNickname = writerNickname; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

