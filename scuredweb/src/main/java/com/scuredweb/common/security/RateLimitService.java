package com.scuredweb.common.security;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimitService {
    /*
     * In-memory rate limiting is suitable for this single-node training app only.
     * Entries are reset on server restart and are not shared across multiple
     * Tomcat instances. In production, replace this with a DB/Redis backed
     * limiter and use X-Forwarded-For only when it is set by a trusted proxy/CDN;
     * never trust client-supplied forwarding headers directly.
     */
    private static final int MAX_FAILURES = 5;
    private static final int MAX_ENTRIES = 10_000;
    private static final long WINDOW_MILLIS = 10L * 60L * 1000L;
    private static final long BLOCK_MILLIS = 10L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 60L * 1000L;
    private static final Map<String, AttemptState> ATTEMPTS = new ConcurrentHashMap<>();
    private static volatile long lastCleanupAt;

    private RateLimitService() {
    }

    public static boolean isBlocked(String scope, String ip, String account) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        String key = key(scope, ip, account);
        AttemptState state = ATTEMPTS.get(key);
        if (state == null) {
            return false;
        }
        if (state.blockedUntil > now) {
            return true;
        }
        if (state.windowStart + WINDOW_MILLIS < now) {
            ATTEMPTS.remove(key);
        }
        return false;
    }

    public static void recordFailure(String scope, String ip, String account) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        ATTEMPTS.compute(key(scope, ip, account), (ignored, state) -> {
            AttemptState current = state == null || state.windowStart + WINDOW_MILLIS < now
                    ? new AttemptState(now)
                    : state;
            current.failures++;
            if (current.failures >= MAX_FAILURES) {
                current.blockedUntil = now + BLOCK_MILLIS;
            }
            return current;
        });
        trimIfNeeded();
    }

    public static void recordSuccess(String scope, String ip, String account) {
        ATTEMPTS.remove(key(scope, ip, account));
    }

    public static synchronized boolean tryConsume(String scope, String ip, int limit) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        String key = key("request-" + scope, ip, "");
        AttemptState state = ATTEMPTS.get(key);
        if (state == null || state.windowStart + WINDOW_MILLIS < now) {
            if (ATTEMPTS.size() >= MAX_ENTRIES) return false;
            state = new AttemptState(now);
            ATTEMPTS.put(key, state);
        }
        if (state.failures >= limit) return false;
        state.failures++;
        return true;
    }

    public static boolean consumeAuthentication(String scope, String ip, String account) {
        String normalized = normalize(account);
        if (normalized.length() > 150) return false;
        int ipLimit = com.scuredweb.common.config.AppConfig.positiveSetting("rate." + scope + ".ip", 100, 10000);
        int accountLimit = com.scuredweb.common.config.AppConfig.positiveSetting("rate." + scope + ".account", 30, 10000);
        int pairLimit = com.scuredweb.common.config.AppConfig.positiveSetting("rate." + scope + ".pair", 10, 10000);
        boolean a = tryConsume(scope + "-ip", ip, ipLimit);
        boolean b = tryConsume(scope + "-account", normalized, accountLimit);
        boolean c = tryConsume(scope + "-pair", normalize(ip) + "|" + normalized, pairLimit);
        return a && b && c;
    }

    private static String key(String scope, String ip, String account) {
        return normalize(scope) + "|" + normalize(ip) + "|" + normalize(account);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void cleanupIfNeeded(long now) {
        if (now - lastCleanupAt < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastCleanupAt = now;
        ATTEMPTS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static void trimIfNeeded() {
        if (ATTEMPTS.size() <= MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        ATTEMPTS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int overflow = ATTEMPTS.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return;
        }
        ATTEMPTS.entrySet().stream()
                .sorted((left, right) -> Long.compare(left.getValue().windowStart, right.getValue().windowStart))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .forEach(ATTEMPTS::remove);
    }

    private static final class AttemptState {
        private final long windowStart;
        private int failures;
        private long blockedUntil;

        private AttemptState(long windowStart) {
            this.windowStart = windowStart;
        }

        private boolean isExpired(long now) {
            return blockedUntil <= now && windowStart + WINDOW_MILLIS < now;
        }
    }
}

