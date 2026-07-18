package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlNormalizeEntry;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.transaction.RecipientIdentity;
import com.spendwise.transaction.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Weekly recipient-name canonicalization (ML strategy phase, 2026-07-13) — populates each
 * transaction's {@code recipient_canonical} column via the FastAPI {@code /normalize-recipients}
 * endpoint, so recurring-payment detection groups spelling variants of one payee together and the
 * UI can show a clean name (docs/spec/decisions.md ADR for canonicalization).
 *
 * <p>Unlike categorization's {@code /predict} (one call per transaction at ingest), canonicalization
 * is inherently a whole-history batch operation: the clustering algorithm compares every recipient
 * name in a user's history against every other, so a name can only be assigned a canonical form
 * relative to the full set. Hence a scheduled sweep rather than an ingest-time hook, on the same
 * weekly cadence as {@link MlRetrainingJob} (the established batch-ML precedent).
 *
 * <p>Cross-user by nature — reads every identity via {@link
 * TransactionService#findAllRecipientIdentities} (the {@code spendwise_jobs} role, BYPASSRLS),
 * groups them by user, and calls the ML gateway <em>once per user</em> (never mixing users'
 * recipients in one clustering call — see {@link
 * com.spendwise.categorization.dto.MlNormalizeRecipientsRequest}). Each user's failure is isolated
 * so one bad user can't stop the rest of the sweep, mirroring {@link CategorizationRetryJob}.
 */
@Component
public class RecipientCanonicalizationJob {

    private static final Logger log = LoggerFactory.getLogger(RecipientCanonicalizationJob.class);

    private final TransactionService transactionService;
    private final CategorizationService categorizationService;

    public RecipientCanonicalizationJob(
            TransactionService transactionService, CategorizationService categorizationService) {
        this.transactionService = transactionService;
        this.categorizationService = categorizationService;
    }

    @Scheduled(cron = "${app.ml.canonicalization-cron}")
    public void canonicalizeWeekly() {
        run();
    }

    /** Package-visible so tests can invoke it directly rather than waiting on the real schedule. */
    void run() {
        List<RecipientIdentity> identities;
        try {
            identities = transactionService.findAllRecipientIdentities();
        } catch (RuntimeException e) {
            // The next scheduled run retries — a transient failure here must not crash the scheduler.
            log.warn("Recipient canonicalization job's identity lookup failed: {}", e.getMessage());
            return;
        }

        Map<UUID, List<RecipientIdentity>> byUser = new LinkedHashMap<>();
        for (RecipientIdentity identity : identities) {
            byUser.computeIfAbsent(identity.userId(), k -> new ArrayList<>()).add(identity);
        }

        for (Map.Entry<UUID, List<RecipientIdentity>> entry : byUser.entrySet()) {
            try {
                canonicalizeUser(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                // Isolate each user (same pattern as CategorizationRetryJob) — a FastAPI error or a
                // bad row for one user must never stop the rest of the sweep.
                log.warn("Recipient canonicalization failed for user {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void canonicalizeUser(UUID userId, List<RecipientIdentity> identities) {
        // The ML request key is the identity's index within this user's list — an opaque handle the
        // ML service echoes back, letting us map each canonical name to the exact identity to write
        // it to (recipient_name/upi_id can't serve as a key directly since either may be null).
        List<MlNormalizeEntry> entries = new ArrayList<>(identities.size());
        for (int i = 0; i < identities.size(); i++) {
            RecipientIdentity identity = identities.get(i);
            entries.add(new MlNormalizeEntry(Integer.toString(i), identity.recipientName(), identity.upiId()));
        }

        MlNormalizeRecipientsResponse response =
                categorizationService.normalizeRecipients(new MlNormalizeRecipientsRequest(entries));
        Map<String, String> canonicalByKey = response.canonicalNames();

        int updated = 0;
        for (int i = 0; i < identities.size(); i++) {
            String canonical = canonicalByKey.get(Integer.toString(i));
            if (canonical == null || canonical.isBlank()) {
                continue;
            }
            RecipientIdentity identity = identities.get(i);
            updated += transactionService.updateCanonicalForIdentity(
                    userId, identity.recipientName(), identity.upiId(), canonical);
        }
        log.info("Canonicalized {} recipient identities ({} transactions updated) for user {}", identities.size(), updated, userId);
    }
}
