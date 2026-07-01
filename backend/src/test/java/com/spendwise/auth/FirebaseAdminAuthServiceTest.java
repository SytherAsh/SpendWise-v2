package com.spendwise.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Required tests for E1-S1-T1: valid token -> UID; expired token -> exception; malformed token -> exception. */
class FirebaseAdminAuthServiceTest {

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
    private final FirebaseAdminAuthService service = new FirebaseAdminAuthService(firebaseAuth);

    @Test
    void validTokenResolvesToUid() throws Exception {
        FirebaseToken decoded = mock(FirebaseToken.class);
        given(decoded.getUid()).willReturn("firebase-uid-123");
        given(decoded.getEmail()).willReturn("user@example.com");
        given(decoded.getClaims()).willReturn(Map.of("phone_number", "+911234567890"));
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(decoded);

        FirebaseVerifiedIdentity identity = service.verifyIdToken("valid-token");

        assertThat(identity.uid()).isEqualTo("firebase-uid-123");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.phoneNumber()).isEqualTo("+911234567890");
    }

    @Test
    void expiredTokenThrowsTypedException() throws Exception {
        FirebaseAuthException expired = mock(FirebaseAuthException.class);
        given(expired.getMessage()).willReturn("Firebase ID token has expired");
        given(firebaseAuth.verifyIdToken(anyString())).willThrow(expired);

        assertThatThrownBy(() -> service.verifyIdToken("expired-token"))
                .isInstanceOf(InvalidFirebaseTokenException.class);
    }

    @Test
    void malformedTokenThrowsTypedException() throws Exception {
        given(firebaseAuth.verifyIdToken(anyString())).willThrow(new IllegalArgumentException("Malformed token"));

        assertThatThrownBy(() -> service.verifyIdToken("not-a-jwt"))
                .isInstanceOf(InvalidFirebaseTokenException.class);
    }
}
