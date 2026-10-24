package com.resumematch.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Slows down password guessing. Failed sign-ins are counted per email (limit 5) and per
 * client IP (limit 30) inside a 15-minute window that starts at the first failure. Once a
 * counter reaches its limit the login is refused until the window ends. A good login
 * clears the email counter only, so one IP cannot reset its own count by also logging in
 * to an account it owns. Counters live in memory and are lost when the server restarts.
 *
 * <p>Demonstrates: {@code ConcurrentHashMap} with atomic {@code compute}, thread safety,
 * {@code java.time.Clock} injected so the class can be tested, static shared instance.</p>
 */
public final class LoginAttemptLimiter {

    /** Failed attempts allowed per email inside one window. */
    public static final int MAX_FAILURES_PER_EMAIL = 5;

    /** Failed attempts allowed per client IP inside one window. */
    public static final int MAX_FAILURES_PER_IP = 30;

    /** Length of the counting window in milliseconds (15 minutes). */
    public static final long WINDOW_MILLIS = 15L * 60 * 1000;

    /** Most email and most IP entries kept at once, so random emails cannot fill the memory. */
    public static final int MAX_TRACKED = 50_000;

    private static final long MILLIS_PER_MINUTE = 60L * 1000;

    /** The one instance shared by every request. */
    private static final LoginAttemptLimiter SHARED = new LoginAttemptLimiter(Clock.systemUTC());

    /** Failure count and the time the current window started. */
    private static final class Counter {
        final int failures;
        final long windowStart;

        Counter(int failures, long windowStart) {
            this.failures = failures;
            this.windowStart = windowStart;
        }
    }

    private final Clock clock;
    private final ConcurrentMap<String, Counter> byEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> byIp = new ConcurrentHashMap<>();
    private volatile long lastSweep;

    /** Creates a limiter that reads the time from the given clock (a fixed or mutable clock in tests). */
    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
        this.lastSweep = clock.millis();
    }

    /** Returns the instance shared by the whole web application. */
    public static LoginAttemptLimiter shared() {
        return SHARED;
    }

    /** True when this email or this IP has used up its failed attempts in the current window. */
    public boolean isBlocked(String email, String ip) {
        return minutesLeft(email, ip) > 0;
    }

    /**
     * Whole minutes (rounded up) until the block on this email or IP ends, or 0 when
     * neither is blocked. When both are blocked the longer wait is returned.
     */
    public int minutesLeft(String email, String ip) {
        long now = clock.millis();
        sweep(now);
        long wait = Math.max(
                remainingMillis(byEmail.get(normalize(email)), MAX_FAILURES_PER_EMAIL, now),
                remainingMillis(byIp.get(normalize(ip)), MAX_FAILURES_PER_IP, now));
        return (int) ((wait + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE);
    }

    /** How many emails are being counted right now (for tests and monitoring). */
    public int trackedEmails() {
        return byEmail.size();
    }

    /** Counts one failed sign-in against both the email and the IP. */
    public void recordFailure(String email, String ip) {
        long now = clock.millis();
        sweep(now);
        increment(byEmail, normalize(email), now);
        increment(byIp, normalize(ip), now);
    }

    /** Clears the failure count for the email after a good login. The IP count is kept. */
    public void recordSuccess(String email) {
        byEmail.remove(normalize(email));
    }

    private long remainingMillis(Counter counter, int limit, long now) {
        if (counter == null || counter.failures < limit) {
            return 0;
        }
        return Math.max(0, counter.windowStart + WINDOW_MILLIS - now);
    }

    private void increment(ConcurrentMap<String, Counter> map, String key, long now) {
        map.compute(key, (k, old) -> {
            if (old == null && map.size() >= MAX_TRACKED) {
                return null; // table full: a new key is not tracked until old ones expire
            }
            if (old == null || now - old.windowStart >= WINDOW_MILLIS) {
                return new Counter(1, now);
            }
            return new Counter(old.failures + 1, old.windowStart);
        });
    }

    /** Removes expired entries, at most once a minute (or at once when a table is full). */
    private void sweep(long now) {
        boolean full = byEmail.size() >= MAX_TRACKED || byIp.size() >= MAX_TRACKED;
        if (!full && now - lastSweep < MILLIS_PER_MINUTE) {
            return;
        }
        lastSweep = now;
        byEmail.entrySet().removeIf(e -> now - e.getValue().windowStart >= WINDOW_MILLIS);
        byIp.entrySet().removeIf(e -> now - e.getValue().windowStart >= WINDOW_MILLIS);
    }

    /**
     * The key an IP address is counted under. An IPv6 client usually owns a whole /64, so
     * only its first 64 bits are used and it cannot dodge the limit by changing the rest.
     * IPv4 and anything that is not an IP address are only trimmed and lower-cased.
     */
    public static String ipKey(String ip) {
        String value = normalize(ip);
        if (value.indexOf(':') < 0 || !value.matches("[0-9a-f:.%]+")) {
            return value;
        }
        try {
            byte[] bytes = InetAddress.getByName(value).getAddress(); // a literal: no DNS lookup
            if (bytes.length != 16) {
                return InetAddress.getByAddress(bytes).getHostAddress();
            }
            StringBuilder key = new StringBuilder("v6/");
            for (int i = 0; i < 8; i++) {
                key.append(String.format("%02x", bytes[i]));
            }
            return key.toString();
        } catch (UnknownHostException e) {
            return value;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
