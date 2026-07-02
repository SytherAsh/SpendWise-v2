package com.spendwise.categorization;

import java.util.UUID;

/**
 * Service interface for the Categorization module — the only module permitted to call the
 * FastAPI ML service (CLAUDE.md invariant). Consumed cross-module by Ingest (E4-S3-T2, via
 * {@link #categorize}) and, in Epic 11, by Admin (via the retrain/evaluate methods added in
 * E4-S3-T5) — never the reverse, and never by any module reaching into {@link MlClient} directly.
 */
public interface CategorizationService {

    /**
     * Calls FastAPI {@code /predict} for the given transaction and writes a confident result to
     * {@code transaction_categories} with {@code assigned_by = 'ml'} (docs/architecture.md
     * "Categorization may call: Transaction (update category)"). Never throws — a failed call or
     * a confidence below the configured threshold leaves the transaction uncategorized so
     * E4-S3-T3's retry job picks it up later, rather than crashing the ingest flow (E4-S3-T1 DoD).
     */
    void categorize(UUID userId, UUID transactionId);
}
