package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlEvaluationResponse;

import java.util.UUID;

/**
 * Service interface for the Categorization module — the only module permitted to call the
 * FastAPI ML service (CLAUDE.md invariant). Consumed cross-module by Ingest (E4-S3-T2, via {@link
 * #categorize}), by this module's own scheduled jobs (E4-S3-T3/T4), and, in Epic 11, by Admin
 * (via {@link #triggerRetrain} and {@link #getAccuracyMetrics}, added in E4-S3-T5) — never the
 * reverse, and never by any module reaching into {@link MlClient} directly.
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

    /**
     * Reads every {@code ml_corrections} row across all users (cross-user; see
     * {@code com.spendwise.common.db.JobsDataSourceConfig}) and sends them to FastAPI {@code
     * /retrain} alongside the baseline dataset, replacing the model artifact (ADR-003: adaptive
     * supervised batch retraining). Called by E4-S3-T4's weekly job and, in Epic 11, by Admin's
     * manual trigger. Propagates on failure — the caller (a {@code @Scheduled} job, or a future
     * Admin endpoint) decides how to handle/report that, this method doesn't swallow it the way
     * {@link #categorize} does.
     *
     * @throws org.springframework.web.client.RestClientException on network failure or non-2xx response
     */
    void triggerRetrain();

    /**
     * Calls FastAPI {@code GET /evaluate} and returns the parsed accuracy report. Needs no
     * per-user backend data — the FastAPI service evaluates against its own labeled dataset.
     *
     * @throws org.springframework.web.client.RestClientException on network failure or non-2xx response
     */
    MlEvaluationResponse getAccuracyMetrics();
}
