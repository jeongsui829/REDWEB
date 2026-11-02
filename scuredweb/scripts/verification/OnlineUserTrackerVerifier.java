package com.scuredweb.common.util;

import com.scuredweb.user.dto.UserDTO;

public final class OnlineUserTrackerVerifier {
    private OnlineUserTrackerVerifier() {
    }

    public static void main(String[] args) {
        long now = 1_000_000L;
        OnlineUserTracker.resetForVerification();
        assertStatus(OnlineUserTracker.statusAt(now), 0, 0, 0, "session creation/login GET baseline");

        OnlineUserTracker.userUpdatedAt("browser-a", null, now);
        assertStatus(OnlineUserTracker.statusAt(now), 1, 0, 1, "anonymous heartbeat");

        UserDTO member = new UserDTO();
        member.setUserId(42L);
        member.setLoginId("member42");
        member.setNickname("Member 42");
        OnlineUserTracker.userUpdatedAt("browser-a", member, now + 1);
        assertStatus(OnlineUserTracker.statusAt(now + 1), 1, 1, 0, "login replaces guest in same session");

        OnlineUserTracker.userUpdatedAt("browser-a", member, now + 2);
        assertStatus(OnlineUserTracker.statusAt(now + 2), 1, 1, 0, "additional tab heartbeat in same session");

        OnlineUserTracker.userUpdatedAt("browser-b", null, now + 2);
        assertStatus(OnlineUserTracker.statusAt(now + 2), 2, 1, 1, "independent browser session");

        assertStatus(OnlineUserTracker.statusAt(now + 120_003L), 0, 0, 0, "heartbeat expiry");
        OnlineUserTracker.resetForVerification();
        System.out.println("OnlineUserTracker verification passed");
    }

    private static void assertStatus(
            OnlineUserTracker.OnlineStatus status,
            int active,
            int authenticated,
            int guest,
            String scenario) {
        if (status.activeSessionCount() != active
                || status.authenticatedSessionCount() != authenticated
                || status.guestSessionCount() != guest) {
            throw new AssertionError(scenario + ": expected " + active + "/" + authenticated + "/" + guest
                    + " but was " + status.activeSessionCount() + "/"
                    + status.authenticatedSessionCount() + "/" + status.guestSessionCount());
        }
    }
}
