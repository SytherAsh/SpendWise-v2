package com.spendwise.auth;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Initializes the Firebase Admin SDK from {@code FIREBASE_PROJECT_ID} / {@code
 * FIREBASE_PRIVATE_KEY} (never hardcoded — E1-S1-T1 DoD). {@code FirebaseAuth.verifyIdToken}
 * validates a token's signature against Google's public JWKS (fetched over HTTPS on demand)
 * and checks the token's {@code aud}/{@code iss} claims against the configured project ID —
 * it does not itself require service-account signing credentials. Real service-account
 * credentials are only needed for other Admin SDK operations this project doesn't use
 * (custom token minting, user management), so when {@code FIREBASE_PRIVATE_KEY} isn't a full
 * service-account JSON document (the documented env var pair captures only two of the several
 * fields a real service-account key contains), initialization falls back to a placeholder
 * credential that performs no network I/O. This keeps app startup deterministic in every
 * environment (local/CI/dev/prod) regardless of which credential shape is supplied.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp(
            @Value("${app.firebase.project-id:}") String projectId,
            @Value("${app.firebase.private-key:}") String privateKey) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder().setCredentials(resolveCredentials(privateKey));
        if (StringUtils.hasText(projectId)) {
            optionsBuilder.setProjectId(projectId);
        } else {
            log.warn("FIREBASE_PROJECT_ID not set — Firebase ID token verification will reject every token"
                    + " until it is configured.");
        }
        return FirebaseApp.initializeApp(optionsBuilder.build());
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    /**
     * Reuses the same {@link FirebaseApp} bean above — Epic 5's Alerts push dispatch (E5-S3-T1)
     * needs no new SDK dependency, just this second client off the existing firebase-admin
     * credential.
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private GoogleCredentials resolveCredentials(String privateKey) {
        if (StringUtils.hasText(privateKey) && privateKey.strip().startsWith("{")) {
            try {
                return GoogleCredentials.fromStream(
                        new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                log.warn("FIREBASE_PRIVATE_KEY was set but could not be parsed as a service-account JSON"
                        + " document; falling back to an unsigned placeholder credential.", e);
            }
        }
        // No network I/O, no external credential source — safe to construct in any
        // environment. Sufficient for verifyIdToken (see class-level note above).
        return GoogleCredentials.create(new AccessToken("unused-placeholder", null));
    }
}
