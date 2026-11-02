package com.redweb.board.dto;

public class TagDTO {
    private Long tagId;
    private String tagName;
    private int useCount;

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }
}

