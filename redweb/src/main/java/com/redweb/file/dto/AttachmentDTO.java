package com.redweb.file.dto;

public class AttachmentDTO {
    private Long attachmentId;
    private Long postId;
    private String originalName;
    private String savedName;
    private String filePath;
    private long fileSize;
    private boolean image;

    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long attachmentId) { this.attachmentId = attachmentId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getSavedName() { return savedName; }
    public void setSavedName(String savedName) { this.savedName = savedName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public boolean isImage() { return image; }
    public void setImage(boolean image) { this.image = image; }
}
