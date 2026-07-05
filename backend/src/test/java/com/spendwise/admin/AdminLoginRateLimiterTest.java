package com.spendwise.admin;

import com.spendwise.auth.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Required test for E11-S1-T1: the 11th admin login attempt from the same IP within 15 minutes is rejected. */
class AdminLoginRateLimiterTest {

    private final AdminLoginRateLimiter rateLimiter = new AdminLoginRateLimiter();

    @Test
    void allowsUpToTenAttemptsPerWindowThenRejectsTheEleventh() {
        String ip = "203.0.113.7";
        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> rateLimiter.checkAndRecord(ip)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.checkAndRecord(ip)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void differentIpsAreTrackedIndependently() {
        String ipA = "203.0.113.7";
        String ipB = "198.51.100.9";
        for (int i = 0; i < 10; i++) {
            rateLimiter.checkAndRecord(ipA);
        }

        assertThatCode(() -> rateLimiter.checkAndRecord(ipB)).doesNotThrowAnyException();
    }
}
