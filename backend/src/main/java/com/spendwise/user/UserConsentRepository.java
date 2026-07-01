package com.spendwise.user;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class UserConsentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public UserConsentRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    /** Snapshots the exact consent text shown to the user at onboarding (docs/security.md DPDP). */
    public void recordConsent(UUID userId, String consentText, String appVersion) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO user_consent (user_id, app_version, consent_text) VALUES (?, ?, ?)",
                userId,
                appVersion,
                consentText);
    }
}
