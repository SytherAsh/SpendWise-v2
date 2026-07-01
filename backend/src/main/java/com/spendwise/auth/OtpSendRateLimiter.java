package com.spendwise.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-phone-number sliding-window rate limiter for {@code /auth/otp/send} (E1-S1-T3, max 5
 * requests/hour per docs/security.md Rate Limiting). In-memory by design — CLAUDE.md's
 * infrastructure constraints rule out Redis/an external cache for a single always-on
 * free-tier instance.
 */
@Component
public class OtpSendRateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Deque<Instant>> requestsByPhone = new ConcurrentHashMap<>();

    /** @throws RateLimitExceededException if this phone number has already sent 5 requests within the last hour */
    public void checkAndRecord(String phone) {
        Instant now = Instant.now();
        requestsByPhone.compute(phone, (key, existing) -> {
            Deque<Instant> timestamps = existing == null ? new ArrayDeque<>() : existing;
            while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).compareTo(WINDOW) > 0) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                throw new RateLimitExceededException("Too many OTP requests for this phone number; try again later");
            }
            timestamps.addLast(now);
            return timestamps;
        });
    }
}
