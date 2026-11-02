package com.redweb.board.service;

import com.redweb.board.dao.BoardDAO;
import com.redweb.board.dto.BoardDTO;
import com.redweb.board.dto.PostDTO;
import com.redweb.board.dto.TagDTO;
import com.redweb.common.util.SampleDataFactory;
import com.redweb.file.dto.AttachmentDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BoardService {
    private final BoardDAO boardDAO = new BoardDAO();

    public List<PostDTO> getLatestPosts() {
        try {
            return boardDAO.findLatestPosts();
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<PostDTO> getTrendingPosts() {
        try {
            return boardDAO.findTrendingPosts();
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<BoardDTO> getBoards() {
        Map<String, BoardDTO> merged = new LinkedHashMap<>();
        for (BoardDTO board : SampleDataFactory.boards()) {
            merged.put(board.getBoardCode(), board);
        }

        try {
            for (BoardDTO board : boardDAO.findBoards()) {
                merged.put(board.getBoardCode(), board);
            }
        } catch (Exception ignored) {
        }

        merged.remove("share");
        merged.remove("archive");

        return new ArrayList<>(merged.values());
    }

    public List<TagDTO> getPopularTags() {
        try {
            List<TagDTO> tags = boardDAO.findPopularTags();
            return tags.isEmpty() ? SampleDataFactory.tags() : tags;
        } catch (Exception exception) {
            return SampleDataFactory.tags();
        }
    }

    public List<PostDTO> getBoardPosts(String boardCode) {
        try {
            List<PostDTO> posts = boardDAO.findBoardPosts(boardCode);
            System.out.println("[DEBUG] getBoardPosts boardCode=" + boardCode + ", posts.size=" + posts.size());
            return posts;
        } catch (Exception exception) {
            System.out.println("[ERROR] getBoardPosts failed. boardCode=" + boardCode);
            exception.printStackTrace();
            return List.of();
        }
    }

    public List<PostDTO> searchBoardPosts(String boardCode, String keyword) {
        try {
            List<PostDTO> posts = boardDAO.searchBoardPosts(boardCode, keyword);
            System.out.println("[DEBUG] searchBoardPosts boardCode=" + boardCode + ", posts.size=" + posts.size());
            return posts;
        } catch (Exception exception) {
            System.out.println("[ERROR] searchBoardPosts failed. boardCode=" + boardCode + ", keyword=" + keyword);
            exception.printStackTrace();
            return List.of();
        }
    }

    public PostDTO getPostDetail(Long postId) {
        if (postId == null) {
            return null;
        }
        PostDTO post = getPostDetail(String.valueOf(postId));
        if (post != null) {
            return post;
        }
        return null;
    }

    public PostDTO getPostDetail(String postIdExpression) {
        try {
            PostDTO post = boardDAO.findPostDetail(postIdExpression);
            if (post != null) {
                return post;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return null;
    }

    public BoardDTO getBoardByCode(String boardCode) {
        try {
            BoardDTO board = boardDAO.findBoardByCode(boardCode);
            if (board != null) {
                return board;
            }
        } catch (Exception ignored) {
        }

        for (BoardDTO board : getBoards()) {
            if (board.getBoardCode().equals(boardCode)) {
                return board;
            }
        }
        return getBoards().isEmpty() ? null : getBoards().getFirst();
    }

    public Long createPost(String boardCode, String title, String content, Long userId) {
        try {
            return boardDAO.insertPost(boardCode, title, content, userId);
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public boolean updatePost(Long postId, Long ownerUserId, String title, String content) {
        try {
            return boardDAO.updatePost(postId, ownerUserId, title, content);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean deletePost(Long postId, Long ownerUserId) {
        try {
            return boardDAO.deletePost(postId, ownerUserId);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean deletePostAsAdmin(Long postId) {
        try {
            return boardDAO.deletePostAsAdmin(postId);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean hidePost(Long postId) {
        try {
            return boardDAO.hidePost(postId);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean restorePost(Long postId) {
        try {
            return boardDAO.restorePost(postId);
        } catch (Exception exception) {
            return false;
        }
    }

    public List<PostDTO> getAllPostsForAdmin() {
        try {
            return boardDAO.findAllPostsForAdmin();
        } catch (Exception exception) {
            return List.of();
        }
    }

    public Long createNotice(String boardCode, String title, String content, Long adminUserId) {
        try {
            return boardDAO.insertNoticePost(boardCode, title, content, adminUserId);
        } catch (Exception exception) {
            return null;
        }
    }

    public void saveAttachments(Long postId, Long uploaderId, List<AttachmentDTO> attachments) {
        try {
            boardDAO.insertAttachments(postId, uploaderId, attachments);
        } catch (Exception ignored) {
        }
    }

    public List<AttachmentDTO> getAttachments(Long postId) {
        try {
            return boardDAO.findAttachmentsByPostId(postId);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public List<PostDTO> searchPosts(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        try {
            List<PostDTO> posts = boardDAO.searchPosts(keyword);
            System.out.println("[DEBUG] searchPosts posts.size=" + posts.size());
            return posts;
        } catch (Exception exception) {
            System.out.println("[ERROR] searchPosts failed. keyword=" + keyword);
            exception.printStackTrace();
            return List.of();
        }
    }
}
