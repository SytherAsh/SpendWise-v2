package com.spendwise.admin;

import com.spendwise.auth.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client-IP sliding-window rate limiter for {@code POST /admin/auth/login} (E11-S1-T1, max 10
 * attempts/15 min per docs/security.md's "Login attempts" policy — documented since Epic 1 but
 * never actually implemented anywhere until now). In-memory by design, same reasoning as {@link
 * com.spendwise.auth.OtpSendRateLimiter}: CLAUDE.md's infrastructure constraints rule out
 * Redis/an external cache for a single always-on free-tier instance. Keyed by IP rather than
 * username since there is exactly one valid admin username — a per-username limiter would offer
 * no more protection than a global one and would leak whether a guessed username is correct.
 */
@Component
public class AdminLoginRateLimiter {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();

    /** @throws RateLimitExceededException if this IP has already attempted 10 logins within the last 15 minutes */
    public void checkAndRecord(String clientIp) {
        Instant now = Instant.now();
        attemptsByIp.compute(clientIp, (key, existing) -> {
            Deque<Instant> timestamps = existing == null ? new ArrayDeque<>() : existing;
            while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).compareTo(WINDOW) > 0) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS_PER_WINDOW) {
                throw new RateLimitExceededException("Too many admin login attempts; try again later");
            }
            timestamps.addLast(now);
            return timestamps;
        });
    }
}
