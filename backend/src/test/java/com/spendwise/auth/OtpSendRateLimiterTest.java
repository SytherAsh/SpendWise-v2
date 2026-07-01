package com.spendwise.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Required test for E1-S1-T3: the 6th OTP-send request for the same phone within an hour is rejected. */
class OtpSendRateLimiterTest {

    private final OtpSendRateLimiter rateLimiter = new OtpSendRateLimiter();

    @Test
    void allowsUpToFiveRequestsPerHourThenRejectsTheSixth() {
        String phone = "+919876543210";
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> rateLimiter.checkAndRecord(phone)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.checkAndRecord(phone)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void differentPhoneNumbersAreTrackedIndependently() {
        String phoneA = "+911111111111";
        String phoneB = "+912222222222";
        for (int i = 0; i < 5; i++) {
            rateLimiter.checkAndRecord(phoneA);
        }

        assertThatCode(() -> rateLimiter.checkAndRecord(phoneB)).doesNotThrowAnyException();
    }
}
