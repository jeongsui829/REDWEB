package com.redweb.admin.service;

import com.redweb.board.dto.PostDTO;
import com.redweb.board.service.BoardService;
import com.redweb.common.util.SampleDataFactory;
import com.redweb.user.dao.UserDAO;
import com.redweb.user.dto.UserDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminOpsService {
    private final UserDAO userDAO = new UserDAO();
    private final BoardService boardService = new BoardService();

    public List<UserDTO> getUsers() {
        try {
            return userDAO.findAllUsers();
        } catch (Exception exception) {
            return List.of(
                    SampleDataFactory.user(),
                    SampleDataFactory.memberOne(),
                    SampleDataFactory.memberTwo()
            );
        }
    }

    public boolean updateUserStatus(Long userId, String status) {
        try {
            return userDAO.updateUserStatus(userId, status);
        } catch (Exception exception) {
            return false;
        }
    }

    public List<PostDTO> getAllPosts(Map<String, List<PostDTO>> localPosts) {
        List<PostDTO> posts = new ArrayList<>();
        for (String boardCode : List.of("free", "daily-log", "ctf-qna", "project-share", "qna",
                "owasp-top10", "security-news", "hacking-basic", "linux-basic", "network-basic", "web-basic", "faq")) {
            posts.addAll(boardService.getBoardPosts(boardCode));
        }
        if (localPosts != null) {
            for (List<PostDTO> items : localPosts.values()) {
                posts.addAll(items);
            }
        }
        return posts;
    }

    public Long createNotice(String title, String content, Long adminUserId) {
        return boardService.createNotice("free", title, content, adminUserId);
    }

    public boolean hidePost(Long postId) {
        return boardService.hidePost(postId);
    }
}
