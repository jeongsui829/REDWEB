package com.scuredweb.board.dto;

public class BoardDTO {
    private Long boardId;
    private String boardCode;
    private String boardName;
    private String description;

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }
    public String getBoardCode() { return boardCode; }
    public void setBoardCode(String boardCode) { this.boardCode = boardCode; }
    public String getBoardName() { return boardName; }
    public void setBoardName(String boardName) { this.boardName = boardName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}


