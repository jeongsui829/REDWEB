package com.scuredweb.file.dto;

public class AttachmentDTO {
    private Long attachmentId;
    private Long postId;
    private String originalName;
    private String savedName;
    private String filePath;
    private Long uploaderId;
    private String fileExt;
    private String mimeType;
    private String postStatus;
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
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public String getFileExt() { return fileExt; }
    public void setFileExt(String fileExt) { this.fileExt = fileExt; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getPostStatus() { return postStatus; }
    public void setPostStatus(String postStatus) { this.postStatus = postStatus; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public boolean isImage() { return image; }
    public void setImage(boolean image) { this.image = image; }
}

