package com.redweb.social.service;

import com.redweb.auth.service.AuthService;
import com.redweb.common.util.SampleDataFactory;
import com.redweb.notification.dto.NotificationDTO;
import com.redweb.social.dao.SocialDAO;
import com.redweb.social.dto.GuestbookEntryDTO;
import com.redweb.social.dto.MessageDTO;
import com.redweb.social.dto.ReportDTO;
import com.redweb.user.dao.UserDAO;
import com.redweb.user.dto.UserDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialService {
    private static final SocialDAO SOCIAL_DAO = new SocialDAO();
    private static final Map<Long, LocalDateTime> SUSPENDED_UNTIL = new ConcurrentHashMap<>();

    private SocialService() {
    }

    public static UserDTO findUser(String nicknameOrLoginId) {
        if (nicknameOrLoginId == null || nicknameOrLoginId.isBlank()) {
            return null;
        }

        try {
            UserDTO dbUser = SOCIAL_DAO.findUser(nicknameOrLoginId.trim());
            if (dbUser != null) {
                AuthService.syncLocalUser(dbUser);
                return dbUser;
            }
        } catch (Exception ignored) {
        }

        String normalized = nicknameOrLoginId.trim().toLowerCase();
        for (UserDTO user : AuthService.getLocalUsers()) {
            if (matchesUser(user, normalized)) {
                return user;
            }
        }
        for (UserDTO user : List.of(SampleDataFactory.user(), SampleDataFactory.memberOne(), SampleDataFactory.memberTwo())) {
            if (matchesUser(user, normalized)) {
                return user;
            }
        }
        return null;
    }

    private static boolean matchesUser(UserDTO user, String normalized) {
        return user != null
                && ((user.getNickname() != null && user.getNickname().trim().toLowerCase().equals(normalized))
                || (user.getLoginId() != null && user.getLoginId().trim().toLowerCase().equals(normalized)));
    }

    public static void enrichUser(UserDTO user) {
        if (user == null || user.getLoginId() == null) {
            return;
        }
        UserDTO dbUser = findUser(user.getLoginId());
        if (dbUser != null && dbUser.getBio() != null) {
            user.setBio(dbUser.getBio());
        }
    }

    public static void updateBio(UserDTO user, String bio) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        user.setBio(bio);
        try {
            SOCIAL_DAO.updateBio(user.getUserId(), bio);
        } catch (Exception ignored) {
        }
        AuthService.syncLocalUser(user);
    }

    public static List<MessageDTO> getMessages(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return SOCIAL_DAO.findMessages(userId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static long getUnreadMessageCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            return SOCIAL_DAO.countUnreadMessages(userId);
        } catch (Exception exception) {
            return 0L;
        }
    }

    public static void markMessagesRead(Long userId) {
        try {
            SOCIAL_DAO.markMessagesRead(userId);
        } catch (Exception ignored) {
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

    public static int deleteReadMessages(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            return SOCIAL_DAO.deleteReadMessages(userId);
        } catch (Exception exception) {
            return 0;
        }
    }

    public static void sendMessage(UserDTO sender, UserDTO receiver, String title, String content) {
        if (sender == null || receiver == null || receiver.getUserId() == null) {
            return;
        }
        try {
            SOCIAL_DAO.insertMessage(sender, receiver, title, content);
            pushNotification(receiver.getUserId(), "새 쪽지가 도착했습니다",
                    sender.getNickname() + " 님이 쪽지를 보냈습니다.", "/messages");
        } catch (Exception ignored) {
        }
    }

    public static List<GuestbookEntryDTO> getGuestbookEntries(Long ownerUserId) {
        if (ownerUserId == null) {
            return List.of();
        }
        try {
            return SOCIAL_DAO.findGuestbookEntries(ownerUserId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static void addGuestbookEntry(UserDTO writer, UserDTO owner, String content) {
        if (writer == null || owner == null || owner.getUserId() == null) {
            return;
        }
        try {
            SOCIAL_DAO.insertGuestbookEntry(writer, owner, content);
            if (!owner.getUserId().equals(writer.getUserId())) {
                pushNotification(owner.getUserId(), "방명록이 등록되었습니다",
                        writer.getNickname() + " 님이 방명록을 남겼습니다.",
                        "/profile?nickname=" + owner.getNickname());
            }
        } catch (Exception ignored) {
        }
    }

    public static List<NotificationDTO> getNotifications(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return SOCIAL_DAO.findNotifications(userId);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static long getUnreadNotificationCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            return SOCIAL_DAO.countUnreadNotifications(userId);
        } catch (Exception exception) {
            return 0L;
        }
    }

    public static void markNotificationsRead(Long userId) {
        try {
            SOCIAL_DAO.markNotificationsRead(userId);
        } catch (Exception ignored) {
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

    public static int deleteReadNotifications(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            return SOCIAL_DAO.deleteReadNotifications(userId);
        } catch (Exception exception) {
            return 0;
        }
    }

    public static void pushNotification(Long userId, String title, String message, String targetUrl) {
        if (userId == null) {
            return;
        }
        try {
            SOCIAL_DAO.insertNotification(userId, title, message, targetUrl);
        } catch (Exception ignored) {
        }
    }

    public static void notifyComment(UserDTO actor, Long postOwnerUserId, String postTitle, Long postId) {
        if (actor == null || postOwnerUserId == null || postOwnerUserId.equals(actor.getUserId())) {
            return;
        }
        pushNotification(postOwnerUserId, "댓글이 등록되었습니다",
                actor.getNickname() + " 님이 '" + postTitle + "' 글에 댓글을 남겼습니다.",
                "/board/detail?id=" + postId);
    }

    public static void submitUserReport(UserDTO reporter, String targetNickname, String reason) {
        if (reporter == null || targetNickname == null || targetNickname.isBlank()) {
            return;
        }

        UserDTO targetUser = findUser(targetNickname);
        if (targetUser == null || targetUser.getUserId() == null) {
            return;
        }

        ReportDTO report = new ReportDTO();
        report.setReporterUserId(reporter.getUserId());
        report.setReporterNickname(reporter.getNickname());
        report.setTargetUserId(targetUser.getUserId());
        report.setTargetNickname(targetUser.getNickname());
        report.setReason(reason == null || reason.isBlank() ? "사용자 신고 접수" : reason);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());

        try {
            SOCIAL_DAO.insertReport(report);
            pushNotification(1L, "새 신고가 접수되었습니다",
                    reporter.getNickname() + " 님이 " + targetUser.getNickname() + " 사용자를 신고했습니다.",
                    "/admin/reports");
        } catch (Exception ignored) {
        }
    }

    public static List<ReportDTO> getReports() {
        try {
            return SOCIAL_DAO.findReports();
        } catch (Exception exception) {
            return List.of();
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

        int reportCount = 1;
        try {
            reportCount = SOCIAL_DAO.countResolvedReportsForTarget(report.getTargetUserId()) + 1;
        } catch (Exception ignored) {
        }

        LocalDateTime suspendedUntil = calculateSuspendedUntil(reportCount);
        String penaltySummary = buildPenaltySummary(reportCount, suspendedUntil);
        try {
            boolean updated = SOCIAL_DAO.updateReportStatus(reportId, adminUser.getUserId(), "RESOLVED", penaltySummary);
            if (!updated) {
                return false;
            }
            SUSPENDED_UNTIL.put(report.getTargetUserId(), suspendedUntil);
            new UserDAO().updateUserStatus(report.getTargetUserId(), "SUSPENDED");
            AuthService.updateLocalUserStatus(report.getTargetUserId(), "SUSPENDED");
            return true;
        } catch (Exception exception) {
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
        LocalDateTime suspendedUntil = SUSPENDED_UNTIL.get(userId);
        return suspendedUntil != null && suspendedUntil.isAfter(LocalDateTime.now());
    }

    public static boolean releaseExpiredSuspension(UserDTO user) {
        if (user == null || user.getUserId() == null) {
            return false;
        }
        LocalDateTime suspendedUntil = SUSPENDED_UNTIL.get(user.getUserId());
        if (suspendedUntil == null || suspendedUntil.isAfter(LocalDateTime.now())) {
            return false;
        }

        SUSPENDED_UNTIL.remove(user.getUserId());
        user.setStatus("ACTIVE");
        AuthService.syncLocalUser(user);
        try {
            new UserDAO().updateUserStatus(user.getUserId(), "ACTIVE");
        } catch (Exception ignored) {
        }
        return true;
    }

    private static ReportDTO findPendingReport(Long reportId) {
        return getReports().stream()
                .filter(report -> reportId.equals(report.getReportId()))
                .filter(report -> "PENDING".equals(report.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private static LocalDateTime calculateSuspendedUntil(int reportCount) {
        if (reportCount <= 3) {
            return LocalDateTime.now().plusMinutes(30);
        }
        if (reportCount == 4) {
            return LocalDateTime.now().plusHours(1);
        }
        return LocalDateTime.now().plusHours(2);
    }

    private static String buildPenaltySummary(int reportCount, LocalDateTime suspendedUntil) {
        if (reportCount <= 3) {
            return reportCount + "회 누적으로 30분 정지";
        }
        if (reportCount == 4) {
            return "4회 누적으로 1시간 정지";
        }
        return reportCount + "회 누적으로 2시간 정지";
    }
}
