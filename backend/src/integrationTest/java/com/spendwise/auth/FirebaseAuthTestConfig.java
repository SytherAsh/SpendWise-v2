package com.spendwise.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * Stand-in for the real Firebase Admin SDK in integration tests — this project has no real
 * Firebase credentials to verify a real ID token against. Recognizes a small fixed set of
 * fake "idToken" strings the tests present, mirroring what a genuinely verified Firebase ID
 * token would resolve to.
 */
@TestConfiguration
public class FirebaseAuthTestConfig {

    public static final String VALID_OTP_TOKEN_PHONE_A = "test-otp-token-phone-a";
    public static final String VALID_GOOGLE_TOKEN_A = "test-google-token-a";

    private static final Map<String, FirebaseVerifiedIdentity> KNOWN_TOKENS = Map.of(
            VALID_OTP_TOKEN_PHONE_A, new FirebaseVerifiedIdentity("firebase-uid-phone-a", "+911111111111", null),
            VALID_GOOGLE_TOKEN_A, new FirebaseVerifiedIdentity("firebase-uid-google-a", null, "usera@example.com"));

    @Bean
    @Primary
    public FirebaseAuthService firebaseAuthService() {
        return idToken -> {
            FirebaseVerifiedIdentity identity = KNOWN_TOKENS.get(idToken);
            if (identity == null) {
                throw new InvalidFirebaseTokenException("Unrecognized test token: " + idToken);
            }
            return identity;
        };
    }
}
