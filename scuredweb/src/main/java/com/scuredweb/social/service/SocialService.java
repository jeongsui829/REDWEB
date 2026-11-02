package com.scuredweb.social.service;

import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.notification.dto.NotificationDTO;
import com.scuredweb.social.dao.SocialDAO;
import com.scuredweb.social.dto.GuestbookEntryDTO;
import com.scuredweb.social.dto.MessageDTO;
import com.scuredweb.social.dto.ReportDTO;
import com.scuredweb.user.dao.UserDAO;
import com.scuredweb.user.dto.UserDTO;
import com.scuredweb.social.dto.PublicProfileDTO;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SocialService {
    private static final Logger LOGGER = Logger.getLogger(SocialService.class.getName());
    private static final SocialDAO SOCIAL_DAO = new SocialDAO();

    private SocialService() {
    }

    public static PublicProfileDTO findPublicProfileByNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return null;
        try {
            return SOCIAL_DAO.findPublicProfileByNickname(nickname.trim());
        } catch (Exception exception) {
            throw new DataAccessException("공개 프로필을 조회하지 못했습니다.", exception);
        }
    }

    public static PublicProfileDTO findUser(String nicknameOrLoginId) {
        if (nicknameOrLoginId == null || nicknameOrLoginId.isBlank()) {
            return null;
        }

        try {
            return SOCIAL_DAO.findUser(nicknameOrLoginId.trim());
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "findUser failed for database lookup", exception);
            throw new DataAccessException("회원 정보를 조회하지 못했습니다.", exception);
        }
    }

    public static void enrichUser(UserDTO user) {
        if (user == null || user.getLoginId() == null) {
            return;
        }
        // UserDAO and SocialDAO both populate BIO; no in-memory fallback is used.
    }

    public record DeleteResult(boolean success, int deletedCount) {
        public static DeleteResult failure() {
            return new DeleteResult(false, 0);
        }
    }

    private record SuspensionPenalty(LocalDateTime suspendedUntil, String summary) {
    }

    public static boolean updateBio(UserDTO user, String bio) {
        if (user == null || user.getUserId() == null) {
            return false;
        }
        try {
            if (!SOCIAL_DAO.updateBio(user.getUserId(), bio)) {
                return false;
            }
            user.setBio(bio);
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "updateBio failed. userId=" + user.getUserId(), exception);
            return false;
        }
    }

    public static List<MessageDTO> getMessages(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return SOCIAL_DAO.findMessages(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "getMessages failed", exception);
            throw new DataAccessException("쪽지 목록을 불러오지 못했습니다.", exception);
        }
    }

    public static long getUnreadMessageCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            return SOCIAL_DAO.countUnreadMessages(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "getUnreadMessageCount failed", exception);
            throw new DataAccessException("읽지 않은 쪽지 수를 불러오지 못했습니다.", exception);
        }
    }

    public static boolean markMessagesRead(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return SOCIAL_DAO.markMessagesRead(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "markMessagesRead failed. userId=" + userId, exception);
            return false;
        }
    }

    public static boolean deleteMessage(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            return false;
        }
        try {
            return SOCIAL_DAO.deleteMessage(userId, messageId);
        } catch (Exception exception) {
            return false;
        }
    }

    public static DeleteResult deleteReadMessages(Long userId) {
        if (userId == null) {
            return DeleteResult.failure();
        }
        try {
            return new DeleteResult(true, SOCIAL_DAO.deleteReadMessages(userId));
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "deleteReadMessages failed. userId=" + userId, exception);
            return DeleteResult.failure();
        }
    }

    public static boolean sendMessage(UserDTO sender, PublicProfileDTO receiver, String title, String content) {
        if (sender == null || receiver == null || receiver.getUserId() == null) {
            return false;
        }
        try {
            SOCIAL_DAO.insertMessage(sender, receiver, title, content);
            if (!pushNotification(receiver.getUserId(), "새 쪽지가 도착했습니다",
                    sender.getNickname() + "님이 쪽지를 보냈습니다.", "/messages")) {
                LOGGER.warning("Message saved but notification failed. receiverUserId=" + receiver.getUserId());
            }
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "sendMessage failed", exception);
            return false;
        }
    }

    public static List<GuestbookEntryDTO> getGuestbookEntries(Long ownerUserId) {
        if (ownerUserId == null) {
            return List.of();
        }
        try {
            return SOCIAL_DAO.findGuestbookEntries(ownerUserId);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "getGuestbookEntries failed", exception);
            throw new DataAccessException("방명록을 불러오지 못했습니다.", exception);
        }
    }

    public static boolean addGuestbookEntry(UserDTO writer, PublicProfileDTO owner, String content) {
        if (writer == null || owner == null || owner.getUserId() == null) {
            return false;
        }
        try {
            SOCIAL_DAO.insertGuestbookEntry(writer, owner, content);
            if (!owner.getUserId().equals(writer.getUserId())) {
                if (!pushNotification(owner.getUserId(), "New guestbook entry",
                        writer.getNickname() + " left a message on your profile.",
                        "/profile?nickname=" + owner.getNickname())) {
                    LOGGER.warning("Guestbook entry saved but notification failed. ownerUserId=" + owner.getUserId());
                }
            }
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "addGuestbookEntry failed", exception);
            return false;
        }
    }

    public static List<NotificationDTO> getNotifications(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return SOCIAL_DAO.findNotifications(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "getNotifications failed", exception);
            throw new DataAccessException("알림 목록을 불러오지 못했습니다.", exception);
        }
    }

    public static long getUnreadNotificationCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            return SOCIAL_DAO.countUnreadNotifications(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "getUnreadNotificationCount failed", exception);
            throw new DataAccessException("읽지 않은 알림 수를 불러오지 못했습니다.", exception);
        }
    }

    public static boolean markNotificationsRead(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return SOCIAL_DAO.markNotificationsRead(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "markNotificationsRead failed. userId=" + userId, exception);
            return false;
        }
    }

    public static boolean deleteNotification(Long userId, Long notificationId) {
        if (userId == null || notificationId == null) {
            return false;
        }
        try {
            return SOCIAL_DAO.deleteNotification(userId, notificationId);
        } catch (Exception exception) {
            return false;
        }
    }

    public static DeleteResult deleteReadNotifications(Long userId) {
        if (userId == null) {
            return DeleteResult.failure();
        }
        try {
            return new DeleteResult(true, SOCIAL_DAO.deleteReadNotifications(userId));
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "deleteReadNotifications failed. userId=" + userId, exception);
            return DeleteResult.failure();
        }
    }

    public static boolean pushNotification(Long userId, String title, String message, String targetUrl) {
        if (userId == null) {
            return false;
        }
        try {
            SOCIAL_DAO.insertNotification(userId, title, message, targetUrl);
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "pushNotification failed. userId=" + userId, exception);
            return false;
        }
    }

    public static void notifyComment(UserDTO actor, Long postOwnerUserId, String postTitle, Long postId) {
        if (actor == null || postOwnerUserId == null || postOwnerUserId.equals(actor.getUserId())) {
            return;
        }
        if (!pushNotification(postOwnerUserId, "새 댓글",
                actor.getNickname() + "님이 '" + postTitle + "' 게시글에 댓글을 남겼습니다.",
                "/board/detail?id=" + postId)) {
            LOGGER.warning("Comment saved but notification failed. postId=" + postId);
        }
    }

    public static boolean submitUserReport(UserDTO reporter, String targetNickname, String reason) {
        if (reporter == null || targetNickname == null || targetNickname.isBlank()) {
            return false;
        }

        PublicProfileDTO targetUser = findUser(targetNickname);
        if (targetUser == null || targetUser.getUserId() == null) {
            return false;
        }

        ReportDTO report = new ReportDTO();
        report.setReporterUserId(reporter.getUserId());
        report.setReporterNickname(reporter.getNickname());
        report.setTargetUserId(targetUser.getUserId());
        report.setTargetNickname(targetUser.getNickname());
        report.setReason(reason == null || reason.isBlank() ? "No reason provided" : reason);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());

        try {
            SOCIAL_DAO.insertReport(report);
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "submitUserReport failed", exception);
            return false;
        }
    }

    public static List<ReportDTO> getReports() {
        try {
            return SOCIAL_DAO.findReports();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "getReports failed", exception);
            throw new DataAccessException("신고 목록을 불러오지 못했습니다.", exception);
        }
    }

    public static boolean resolveReport(Long reportId, UserDTO adminUser) {
        if (reportId == null || adminUser == null) {
            return false;
        }
        ReportDTO report = findPendingReport(reportId);
        if (report == null || report.getTargetUserId() == null) {
            return false;
        }

        int reportCount;
        try {
            reportCount = SOCIAL_DAO.countResolvedReportsForTarget(report.getTargetUserId()) + 1;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "resolveReport aborted while counting reports. targetUserId=" + report.getTargetUserId(),
                    exception);
            return false;
        }

        SuspensionPenalty penalty = calculatePenalty(reportCount);
        try {
            boolean updated = SOCIAL_DAO.resolveReportAndSuspend(
                    reportId,
                    adminUser.getUserId(),
                    report.getTargetUserId(),
                    penalty.summary(),
                    penalty.suspendedUntil());
            if (!updated) {
                return false;
            }
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "resolveReport failed. reportId=" + reportId, exception);
            return false;
        }
    }

    public static boolean dismissReport(Long reportId, UserDTO adminUser) {
        if (reportId == null || adminUser == null) {
            return false;
        }
        try {
            return SOCIAL_DAO.updateReportStatus(reportId, adminUser.getUserId(), "DISMISSED", "조치 없음");
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean isSuspended(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return new UserDAO().isUserSuspended(userId);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "isSuspended failed. userId=" + userId, exception);
            return true;
        }
    }

    public static boolean releaseExpiredSuspension(UserDTO user) {
        if (user == null || user.getUserId() == null) {
            return false;
        }
        try {
            if (!new UserDAO().releaseExpiredSuspension(user.getUserId())) {
                return false;
            }
            user.setStatus("ACTIVE");
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "releaseExpiredSuspension failed. userId=" + user.getUserId(), exception);
            return false;
        }
    }

    private static ReportDTO findPendingReport(Long reportId) {
        return getReports().stream()
                .filter(report -> reportId.equals(report.getReportId()))
                .filter(report -> "PENDING".equals(report.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private static SuspensionPenalty calculatePenalty(int reportCount) {
        Duration duration = reportCount <= 3
                ? Duration.ofMinutes(30)
                : reportCount == 4 ? Duration.ofHours(1) : Duration.ofHours(2);
        String summary = reportCount + "회 누적으로 "
                + (duration.toMinutes() < 60 ? duration.toMinutes() + "분" : duration.toHours() + "시간")
                + " 정지";
        return new SuspensionPenalty(LocalDateTime.now().plus(duration), summary);
    }
}

