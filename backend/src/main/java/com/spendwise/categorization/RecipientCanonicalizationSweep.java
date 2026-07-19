package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlAmbiguousCandidate;
import com.spendwise.categorization.dto.MlAmbiguousGroup;
import com.spendwise.categorization.dto.MlNormalizeEntry;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.common.db.AdminEventLog;
import com.spendwise.transaction.NewMergeSuggestion;
import com.spendwise.transaction.RecipientCanonicalOverride;
import com.spendwise.transaction.RecipientIdentity;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UnorderedPairKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The actual cross-user recipient-canonicalization sweep (ML strategy phase, 2026-07-13) —
 * extracted out of {@link RecipientCanonicalizationJob} so both the weekly schedule and Admin's
 * manual trigger ({@link CategorizationServiceImpl#triggerCanonicalizationSweep}) run the exact
 * same code path instead of two copies that can drift apart. Deliberately a plain static utility
 * rather than a {@code @Component} — the job and the service each already have their own injected
 * {@link TransactionService}/{@link CategorizationService}, so there is nothing to inject here and
 * no risk of a circular bean dependency between "the job that can trigger the service" and "the
 * service that can trigger the job."
 *
 * <p>Every user-pinned {@code recipient_canonicalization_overrides} row (ADR-014, written via
 * {@code TransactionService#correctPayeeName}) wins over this run's ML answer for the same
 * identity — fetched once up front alongside the identity list, so a user's correction survives
 * every subsequent weekly resweep instead of being silently recomputed away.
 *
 * <p>The same ML response also carries {@code ambiguous_groups} (Merge Payees feature,
 * 2026-07-19) — anchor/candidate identity pairs the clustering considered but did not confidently
 * auto-merge. Each new (not-already-suggested-or-resolved) pair is persisted as a {@code
 * recipient_merge_suggestions} row for the user to review; a candidate identity that already has
 * its own override pinned is skipped (it's already been resolved against some other anchor, and
 * re-suggesting it would just re-litigate a settled question).
 *
 * <p>Every catch below both logs (as before) and writes an {@code admin_logs} {@code
 * canonicalization_failure} event via {@link AdminEventLog} — a swallowed failure here (e.g. the
 * FastAPI service being unreachable) previously only showed up in whichever console happened to be
 * running the backend; now it's visible in the admin portal's Logs view too. {@code AdminEventLog}
 * itself never throws, so this can never turn an already-handled failure into an unhandled one.
 */
final class RecipientCanonicalizationSweep {

    private static final Logger log = LoggerFactory.getLogger(RecipientCanonicalizationSweep.class);

    private RecipientCanonicalizationSweep() {}

    /** Cross-user by nature — reads every identity via the {@code spendwise_jobs} role and groups
     * by user, calling the ML gateway once per user (never mixing users' recipients in one
     * clustering call). Each user's failure is isolated so one bad user can't stop the rest. */
    static void run(TransactionService transactionService, CategorizationService categorizationService, AdminEventLog adminEventLog) {
        List<RecipientIdentity> identities;
        try {
            identities = transactionService.findAllRecipientIdentities();
        } catch (RuntimeException e) {
            // The next scheduled run (or the next manual trigger) retries — a transient failure
            // here must not crash the caller.
            log.warn("Recipient canonicalization job's identity lookup failed: {}", e.getMessage());
            adminEventLog.record("canonicalization_failure", null, Map.of("stage", "identity_lookup", "error", String.valueOf(e.getMessage())));
            return;
        }

        // ADR-014: a user's own correction (TransactionService#correctPayeeName) must permanently
        // win over this run's ML answer for the same identity — fetched once up front (not per
        // user) since it's a single small cross-user table read, same shape as the identity read
        // above. A lookup failure here must not block the whole sweep either; it just means this
        // run falls back to the ML clustering result for every identity, same as before ADR-014.
        Map<RecipientIdentity, String> overrides;
        try {
            overrides = transactionService.findAllCanonicalOverrides().stream()
                    .collect(Collectors.toMap(
                            o -> new RecipientIdentity(o.userId(), o.recipientName(), o.upiId()),
                            RecipientCanonicalOverride::canonicalName,
                            (first, second) -> second));
        } catch (RuntimeException e) {
            log.warn("Recipient canonicalization job's override lookup failed: {}", e.getMessage());
            adminEventLog.record("canonicalization_failure", null, Map.of("stage", "override_lookup", "error", String.valueOf(e.getMessage())));
            overrides = Map.of();
        }

        Map<UUID, List<RecipientIdentity>> byUser = new LinkedHashMap<>();
        for (RecipientIdentity identity : identities) {
            byUser.computeIfAbsent(identity.userId(), k -> new ArrayList<>()).add(identity);
        }

        for (Map.Entry<UUID, List<RecipientIdentity>> entry : byUser.entrySet()) {
            try {
                canonicalizeUser(transactionService, categorizationService, adminEventLog, entry.getKey(), entry.getValue(), overrides);
            } catch (RuntimeException e) {
                log.warn("Recipient canonicalization failed for user {}: {}", entry.getKey(), e.getMessage());
                adminEventLog.record(
                        "canonicalization_failure",
                        entry.getKey(),
                        Map.of("stage", "canonicalize_user", "error", String.valueOf(e.getMessage())));
            }
        }
        adminEventLog.record("canonicalization_run", null, Map.of("status", "success", "usersProcessed", byUser.size()));
    }

    private static void canonicalizeUser(
            TransactionService transactionService,
            CategorizationService categorizationService,
            AdminEventLog adminEventLog,
            UUID userId,
            List<RecipientIdentity> identities,
            Map<RecipientIdentity, String> overrides) {
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
            RecipientIdentity identity = identities.get(i);
            String canonical = overrides.getOrDefault(identity, canonicalByKey.get(Integer.toString(i)));
            if (canonical == null || canonical.isBlank()) {
                continue;
            }
            updated += transactionService.updateCanonicalForIdentity(
                    userId, identity.recipientName(), identity.upiId(), canonical);
        }
        log.info("Canonicalized {} recipient identities ({} transactions updated) for user {}", identities.size(), updated, userId);

        try {
            recordAmbiguousGroups(transactionService, userId, identities, overrides, canonicalByKey, response.ambiguousGroups());
        } catch (RuntimeException e) {
            // Merge Payees suggestions are additive — a failure here must never undo or block the
            // canonical-name writes this user's sweep just made above.
            log.warn("Recording merge suggestions failed for user {}: {}", userId, e.getMessage());
            adminEventLog.record(
                    "canonicalization_failure", userId, Map.of("stage", "merge_suggestions", "error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * Merge Payees (2026-07-19) — persists new review candidates from this run's {@code
     * ambiguous_groups}, skipping anything already suggested/resolved (in either anchor/candidate
     * order — see {@link UnorderedPairKey}) and any candidate identity that already has its own
     * override pinned (already resolved against some other anchor).
     */
    private static void recordAmbiguousGroups(
            TransactionService transactionService,
            UUID userId,
            List<RecipientIdentity> identities,
            Map<RecipientIdentity, String> overrides,
            Map<String, String> canonicalByKey,
            List<MlAmbiguousGroup> ambiguousGroups) {
        if (ambiguousGroups.isEmpty()) {
            return;
        }

        // Mutable copy: also grows as we go, so two ambiguous groups in the SAME run never
        // suggest the identical pair twice (e.g. a candidate ambiguous against two anchors that
        // are themselves each other's candidate in a different group).
        Set<UnorderedPairKey> seenPairs = new HashSet<>(transactionService.findExistingMergeSuggestionPairs(userId));
        List<NewMergeSuggestion> newSuggestions = new ArrayList<>();

        for (MlAmbiguousGroup group : ambiguousGroups) {
            RecipientIdentity anchor = identityForKey(identities, group.anchorKey());
            if (anchor == null) {
                continue;
            }
            // The identity's actual resolved canonical name (an override always wins, same
            // priority as the main write loop above) — not just the ML's raw cluster label — so
            // confirming "same" later assigns the candidate the name that's really in effect.
            String anchorCanonicalName = overrides.getOrDefault(anchor, canonicalByKey.getOrDefault(group.anchorKey(), group.anchorName()));
            if (anchorCanonicalName == null || anchorCanonicalName.isBlank()) {
                continue;
            }

            for (MlAmbiguousCandidate candidate : group.candidates()) {
                RecipientIdentity candidateIdentity = identityForKey(identities, candidate.key());
                if (candidateIdentity == null || overrides.containsKey(candidateIdentity)) {
                    continue;
                }
                UnorderedPairKey pairKey = UnorderedPairKey.of(
                        anchor.recipientName(), anchor.upiId(), candidateIdentity.recipientName(), candidateIdentity.upiId());
                if (!seenPairs.add(pairKey)) {
                    continue; // already known (from a prior run) or already added earlier in this same run
                }
                newSuggestions.add(new NewMergeSuggestion(
                        anchor.recipientName(),
                        anchor.upiId(),
                        anchorCanonicalName,
                        candidateIdentity.recipientName(),
                        candidateIdentity.upiId(),
                        candidate.score(),
                        candidate.reason()));
            }
        }

        if (!newSuggestions.isEmpty()) {
            transactionService.recordMergeSuggestions(userId, newSuggestions);
        }
    }

    private static RecipientIdentity identityForKey(List<RecipientIdentity> identities, String key) {
        int index;
        try {
            index = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return null;
        }
        return index >= 0 && index < identities.size() ? identities.get(index) : null;
    }
}
