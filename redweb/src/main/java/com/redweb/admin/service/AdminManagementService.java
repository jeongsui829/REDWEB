package com.redweb.admin.service;

import com.redweb.auth.service.AuthService;
import com.redweb.board.dto.BoardDTO;
import com.redweb.board.dto.PostDTO;
import com.redweb.board.service.BoardService;
import com.redweb.common.util.SampleDataFactory;
import com.redweb.social.dto.ReportDTO;
import com.redweb.social.service.SocialService;
import com.redweb.user.dao.UserDAO;
import com.redweb.user.dto.UserDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminManagementService {
    private final UserDAO userDAO = new UserDAO();
    private final BoardService boardService = new BoardService();

    public List<UserDTO> getUsers() {
        Map<Long, UserDTO> merged = new LinkedHashMap<>();

        try {
            for (UserDTO user : userDAO.findAllUsers()) {
                merged.put(user.getUserId(), user);
            }
        } catch (Exception ignored) {
        }

        for (UserDTO localUser : AuthService.getLocalUsers()) {
            merged.putIfAbsent(localUser.getUserId(), localUser);
        }

        if (merged.isEmpty()) {
            merged.put(1L, SampleDataFactory.user());
            merged.put(2L, SampleDataFactory.memberOne());
            merged.put(3L, SampleDataFactory.memberTwo());
        }

        List<UserDTO> users = new ArrayList<>(merged.values());
        users.sort(Comparator.comparing(UserDTO::getUserId, Comparator.nullsLast(Long::compareTo)));
        return users;
    }

    public boolean updateUserStatus(Long userId, String status) {
        boolean updated = false;
        try {
            updated = userDAO.updateUserStatus(userId, status);
        } catch (Exception ignored) {
        }
        boolean localUpdated = AuthService.updateLocalUserStatus(userId, status);
        return updated || localUpdated;
    }

    public List<PostDTO> getPosts() {
        return boardService.getAllPostsForAdmin();
    }

    public List<BoardDTO> getBoards() {
        return boardService.getBoards();
    }

    public boolean hidePost(Long postId) {
        return boardService.hidePost(postId);
    }

    public boolean restorePost(Long postId) {
        return boardService.restorePost(postId);
    }

    public boolean deletePost(Long postId) {
        return boardService.deletePostAsAdmin(postId);
    }

    public Long createNotice(String boardCode, String title, String content, Long adminUserId) {
        return boardService.createNotice(boardCode, title, content, adminUserId);
    }

    public List<ReportDTO> getReports() {
        return SocialService.getReports();
    }

    public boolean resolveReport(Long reportId, UserDTO adminUser) {
        return SocialService.resolveReport(reportId, adminUser);
    }

    public boolean dismissReport(Long reportId, UserDTO adminUser) {
        return SocialService.dismissReport(reportId, adminUser);
    }
}
