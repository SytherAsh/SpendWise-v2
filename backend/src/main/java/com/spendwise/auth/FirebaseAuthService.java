package com.spendwise.auth;

/**
 * Verifies phone-OTP and Google credentials server-side so the backend never trusts a
 * client-asserted identity (CLAUDE.md Auth pattern note). Implemented via the Firebase
 * Admin SDK in {@link FirebaseAdminAuthService}.
 */
public interface FirebaseAuthService {

    /**
     * Verifies a Firebase ID token and resolves it to a Firebase identity.
     *
     * @throws InvalidFirebaseTokenException if the token is invalid, expired, or malformed
     */
    FirebaseVerifiedIdentity verifyIdToken(String idToken);
}
