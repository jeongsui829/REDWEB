package com.scuredweb.admin.service;

import com.scuredweb.admin.dao.AdminAuditDAO;
import com.scuredweb.board.dto.BoardDTO;
import com.scuredweb.board.dto.PostDTO;
import com.scuredweb.board.service.BoardService;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.social.dto.ReportDTO;
import com.scuredweb.social.service.SocialService;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminManagementService {
    private static final Logger LOGGER = Logger.getLogger(AdminManagementService.class.getName());
    private final UserDAO userDAO = new UserDAO();
    private final BoardService boardService = new BoardService();
    private final AdminAuditDAO adminAuditDAO = new AdminAuditDAO();

    public record ActionResult(boolean completed, boolean auditLogged) {
        public static ActionResult failed() {
            return new ActionResult(false, false);
        }
    }

    public List<UserDTO> getUsers() {
        try {
            return userDAO.findAllUsers();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "getUsers failed", exception);
            throw new DataAccessException("회원 목록을 불러오지 못했습니다.", exception);
        }
    }

    public ActionResult updateUserStatus(Long userId, String status, UserDTO adminUser) {
        if (adminUser == null || adminUser.getUserId() == null) {
            return ActionResult.failed();
        }
        try {
            boolean updated = userDAO.updateUserStatusByAdmin(userId, status, adminUser.getUserId());
            return new ActionResult(updated, updated);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "updateUserStatus failed. userId=" + userId, exception);
            return ActionResult.failed();
        }
    }

    public List<PostDTO> getPosts() {
        return boardService.getAllPostsForAdmin();
    }

    public List<BoardDTO> getBoards() {
        return boardService.getBoards();
    }

    public ActionResult hidePost(Long postId, UserDTO adminUser) { return visibility(postId,adminUser,true); }
    public ActionResult restorePost(Long postId, UserDTO adminUser) { return visibility(postId,adminUser,false); }
    private ActionResult visibility(Long id, UserDTO admin, boolean hide) {
        if(admin==null) return ActionResult.failed();
        try { boolean ok=new com.scuredweb.board.dao.BoardDAO().moderatePost(id,admin.getUserId(),hide); return new ActionResult(ok,ok); }
        catch(Exception e) { return ActionResult.failed(); }
    }
    public ActionResult deletePost(Long postId, UserDTO adminUser) {
        if(adminUser==null) return ActionResult.failed();
        boolean ok=boardService.deletePostAsAdmin(postId,adminUser.getUserId());
        return new ActionResult(ok,ok);
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

    public boolean recordAction(UserDTO adminUser, String actionType, String targetType, Long targetId, String detail) {
        if (adminUser == null || adminUser.getUserId() == null) {
            return false;
        }
        try {
            return adminAuditDAO.insertAction(adminUser.getUserId(), actionType, targetType, targetId, detail);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Administrator action log failed. actionType=" + actionType + ", targetId=" + targetId,
                    exception);
            return false;
        }
    }

    private ActionResult audited(
            boolean completed, UserDTO adminUser, String actionType, String targetType, Long targetId, String detail) {
        if (!completed) {
            return ActionResult.failed();
        }
        return new ActionResult(true, recordAction(adminUser, actionType, targetType, targetId, detail));
    }
}

