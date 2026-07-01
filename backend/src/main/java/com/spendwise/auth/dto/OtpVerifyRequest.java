package com.spendwise.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code idToken} is the Firebase ID token the client obtained after completing phone-OTP
 * confirmation via the Firebase client SDK (Admin SDK cannot itself send SMS OTPs — see
 * FirebaseConfig). {@code phone} is asserted by the client only for readability/logging; the
 * authoritative phone number is the one embedded in the verified token's claims.
 */
public record OtpVerifyRequest(@NotBlank String phone, @NotBlank String idToken) {}
