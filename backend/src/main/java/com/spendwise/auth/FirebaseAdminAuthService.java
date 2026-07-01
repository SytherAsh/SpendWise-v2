package com.spendwise.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseAdminAuthService implements FirebaseAuthService {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAdminAuthService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public FirebaseVerifiedIdentity verifyIdToken(String idToken) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            String phoneNumber = (String) decoded.getClaims().get("phone_number");
            return new FirebaseVerifiedIdentity(decoded.getUid(), phoneNumber, decoded.getEmail());
        } catch (FirebaseAuthException e) {
            throw new InvalidFirebaseTokenException("Firebase ID token verification failed: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new InvalidFirebaseTokenException("Malformed Firebase ID token", e);
        }
    }
}
