package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlEvaluationResponse;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.categorization.dto.MlRecurringPredictionRequest;
import com.spendwise.categorization.dto.MlRecurringPredictionResponse;

import java.util.UUID;

/**
 * Service interface for the Categorization module — the only module permitted to call the
 * FastAPI ML service (CLAUDE.md invariant). Consumed cross-module by Ingest (E4-S3-T2, via {@link
 * #categorize}), by this module's own scheduled jobs (E4-S3-T3/T4), by Admin (via {@link
 * #triggerRetrain} and {@link #getAccuracyMetrics}, added in E4-S3-T5), and now by Alerts (ML
 * strategy phase, 2026-07-11, via {@link #predictRecurring}) — never the reverse, and never by any
 * module reaching into {@link MlClient} directly. Categorization acting as the ML gateway for a
 * second capability (not just transaction categorization) is a deliberate widening of this
 * module's role rather than a new exception to the "only Categorization calls FastAPI" invariant
 * — see docs/spec/decisions.md. The same reasoning covers {@link #normalizeRecipients} (ML
 * strategy phase, 2026-07-13): recipient-name canonicalization is a third ML capability routed
 * through this gateway, consumed by {@code RecipientCanonicalizationJob}.
 */
public interface CategorizationService {

    /**
     * Calls FastAPI {@code /predict} for the given transaction and writes the result to
     * {@code transaction_categories} with {@code assigned_by = 'ml'} (docs/architecture.md
     * "Categorization may call: Transaction (update category)"). Never throws — a failed call
     * leaves the transaction uncategorized so E4-S3-T3's retry job picks it up later, rather than
     * crashing the ingest flow (E4-S3-T1 DoD). A confidence below the configured threshold is
     * written as the Miscellaneous fallback category instead (docs/spec/requirements.md: "One-off
     * unmatched transaction → defaults to Miscellaneous") — the retry job still re-attempts these
     * rows (see {@link #lowConfidenceThreshold}) so a later retrain can still upgrade them to a
     * better category.
     */
    void categorize(UUID userId, UUID transactionId);

    /**
     * The confidence threshold below which {@link #categorize} falls back to the Miscellaneous
     * category. Exposed so {@code CategorizationRetryJob} can identify which already-assigned
     * transactions are fallback assignments still eligible for a retry, without duplicating this
     * module's configuration.
     */
    double lowConfidenceThreshold();

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

    /**
     * Calls FastAPI {@code /predict-recurring} for one candidate group's features and returns the
     * raw response — unlike {@link #categorize}, this never writes to the database itself; Alerts
     * (the only caller) owns what happens with the result (creating a {@code recurring_payment}
     * alert, or not). Propagates on failure rather than swallowing it, since the caller
     * ({@code AlertEvaluatorJob}) already wraps each user's evaluation in its own try/catch — same
     * reasoning as {@link #triggerRetrain}'s contract.
     *
     * @throws org.springframework.web.client.RestClientException on network failure or non-2xx response
     */
    MlRecurringPredictionResponse predictRecurring(MlRecurringPredictionRequest request);

    /**
     * Calls FastAPI {@code /normalize-recipients} for one user's full recipient set and returns the
     * canonical-name map — like {@link #predictRecurring}, a pure ML passthrough that never writes
     * to the database itself; {@code RecipientCanonicalizationJob} (the only caller) owns reading
     * the identities and writing the results back via {@link
     * com.spendwise.transaction.TransactionService#updateCanonicalForIdentity}. Propagates on
     * failure rather than swallowing it, since the job wraps each user in its own try/catch — same
     * contract as {@link #predictRecurring}.
     *
     * @throws org.springframework.web.client.RestClientException on network failure or non-2xx response
     */
    MlNormalizeRecipientsResponse normalizeRecipients(MlNormalizeRecipientsRequest request);
}
