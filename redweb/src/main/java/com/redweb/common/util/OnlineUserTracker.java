package com.redweb.common.util;

import com.redweb.user.dto.UserDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlineUserTracker {
    private static final long ACTIVE_WINDOW_MILLIS = 120_000L;
    private static final ConcurrentHashMap<String, OnlineUser> ONLINE_USERS = new ConcurrentHashMap<>();
    private static final Comparator<OnlineUser> DISPLAY_ORDER =
            Comparator.comparing(OnlineUser::authenticated).reversed()
                    .thenComparing(Comparator.comparingLong(OnlineUser::lastSeenAt).reversed());

    private OnlineUserTracker() {
    }

    public static void sessionEnded(String sessionId) {
        if (sessionId != null) {
            ONLINE_USERS.remove(sessionId);
        }
    }

    public static void sessionIdChanged(String oldSessionId, String newSessionId) {
        if (oldSessionId == null || newSessionId == null || oldSessionId.equals(newSessionId)) {
            return;
        }
        OnlineUser existing = ONLINE_USERS.remove(oldSessionId);
        if (existing != null) {
            ONLINE_USERS.put(newSessionId, new OnlineUser(
                    newSessionId,
                    existing.userId(),
                    existing.displayName(),
                    existing.authenticated(),
                    existing.lastSeenAt()
            ));
        }
    }

    public static void userUpdated(String sessionId, UserDTO user) {
        userUpdatedAt(sessionId, user, System.currentTimeMillis());
    }

    static void userUpdatedAt(String sessionId, UserDTO user, long now) {
        if (sessionId == null) {
            return;
        }
        String displayName = "Guest";
        Long userId = null;
        boolean authenticated = false;
        if (user != null) {
            displayName = user.getNickname() != null && !user.getNickname().isBlank()
                    ? user.getNickname()
                    : user.getLoginId();
            userId = user.getUserId();
            authenticated = true;
        }
        ONLINE_USERS.put(sessionId, new OnlineUser(
                sessionId,
                userId,
                displayName == null || displayName.isBlank() ? "Member" : displayName,
                authenticated,
                now
        ));
    }

    public static OnlineStatus status() {
        return statusAt(System.currentTimeMillis());
    }

    static OnlineStatus statusAt(long now) {
        long cutoff = now - ACTIVE_WINDOW_MILLIS;
        ONLINE_USERS.entrySet().removeIf(entry -> entry.getValue().lastSeenAt() < cutoff);
        List<OnlineUser> activeSessions = ONLINE_USERS.values().stream()
                .sorted(DISPLAY_ORDER)
                .toList();

        int authenticatedSessionCount = 0;
        Map<String, OnlineUser> uniqueAuthenticatedUsers = new LinkedHashMap<>();
        for (OnlineUser onlineUser : activeSessions) {
            if (!onlineUser.authenticated()) {
                continue;
            }
            authenticatedSessionCount++;
            String identityKey = onlineUser.userId() != null
                    ? "id:" + onlineUser.userId()
                    : "name:" + onlineUser.displayName().toLowerCase(Locale.ROOT);
            uniqueAuthenticatedUsers.putIfAbsent(identityKey, onlineUser);
        }

        int activeSessionCount = activeSessions.size();
        return new OnlineStatus(
                activeSessionCount,
                authenticatedSessionCount,
                activeSessionCount - authenticatedSessionCount,
                new ArrayList<>(uniqueAuthenticatedUsers.values())
        );
    }

    static void resetForVerification() {
        ONLINE_USERS.clear();
    }

    public record OnlineStatus(
            int activeSessionCount,
            int authenticatedSessionCount,
            int guestSessionCount,
            List<OnlineUser> users
    ) {
        public OnlineStatus {
            users = List.copyOf(users);
        }
    }

    public record OnlineUser(
            String sessionId,
            Long userId,
            String displayName,
            boolean authenticated,
            long lastSeenAt
    ) {
    }
}
