package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlEvaluationResponse;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.categorization.dto.MlPredictionRequest;
import com.spendwise.categorization.dto.MlPredictionResponse;
import com.spendwise.categorization.dto.MlRecurringPredictionRequest;
import com.spendwise.categorization.dto.MlRecurringPredictionResponse;
import com.spendwise.categorization.dto.MlRetrainCorrection;
import com.spendwise.categorization.dto.MlRetrainRequest;
import com.spendwise.common.db.AdminEventLog;
import com.spendwise.transaction.MlCorrectionRecord;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CategorizationServiceImpl implements CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationServiceImpl.class);

    private final MlClient mlClient;
    private final TransactionService transactionService;
    private final AdminEventLog adminEventLog;
    private final double lowConfidenceThreshold;
    private final int fallbackCategoryId;

    public CategorizationServiceImpl(
            MlClient mlClient,
            TransactionService transactionService,
            AdminEventLog adminEventLog,
            @Value("${app.ml.low-confidence-threshold}") double lowConfidenceThreshold,
            @Value("${app.ml.fallback-category-id}") int fallbackCategoryId) {
        this.mlClient = mlClient;
        this.transactionService = transactionService;
        this.adminEventLog = adminEventLog;
        this.lowConfidenceThreshold = lowConfidenceThreshold;
        this.fallbackCategoryId = fallbackCategoryId;
    }

    @Override
    public void categorize(UUID userId, UUID transactionId) {
        try {
            Transaction transaction = transactionService.getById(userId, transactionId);
            MlPredictionResponse response = mlClient.predict(toPredictionRequest(transaction));

            if (response.confidence() < lowConfidenceThreshold) {
                log.info(
                        "Low-confidence prediction ({}) for transaction {} — assigning Miscellaneous fallback, "
                                + "eligible for retry until a later retrain improves confidence",
                        response.confidence(),
                        transactionId);
                transactionService.assignMlCategory(userId, transactionId, fallbackCategoryId, response.confidence());
                return;
            }

            transactionService.assignMlCategory(userId, transactionId, response.categoryId(), response.confidence());
        } catch (RuntimeException e) {
            // Never propagate — a failed /predict call (FastAPI unavailable, network error,
            // unknown transaction) must not crash the ingest flow (E4-S3-T1 DoD). The transaction
            // is left uncategorized; E4-S3-T3's retry job re-attempts it later.
            log.warn("Categorization failed for transaction {}: {}", transactionId, e.getMessage());
        }
    }

    @Override
    public double lowConfidenceThreshold() {
        return lowConfidenceThreshold;
    }

    @Override
    public void triggerRetrain() {
        List<MlCorrectionRecord> corrections = transactionService.findAllCorrections();
        List<MlRetrainCorrection> payload = corrections.stream().map(CategorizationServiceImpl::toRetrainCorrection).toList();
        mlClient.retrain(new MlRetrainRequest(payload));
    }

    @Override
    public MlEvaluationResponse getAccuracyMetrics() {
        return mlClient.evaluate();
    }

    @Override
    public MlRecurringPredictionResponse predictRecurring(MlRecurringPredictionRequest request) {
        return mlClient.predictRecurring(request);
    }

    @Override
    public MlNormalizeRecipientsResponse normalizeRecipients(MlNormalizeRecipientsRequest request) {
        return mlClient.normalizeRecipients(request);
    }

    @Override
    public void triggerCanonicalizationSweep() {
        RecipientCanonicalizationSweep.run(transactionService, this, adminEventLog);
    }

    @Override
    public int recategorizeIdentity(UUID userId, String recipientName, String upiId) {
        List<UUID> transactionIds = transactionService.findTransactionIdsForIdentityAsUser(userId, recipientName, upiId);
        for (UUID transactionId : transactionIds) {
            // categorize() already isolates its own failures (never throws, logs and leaves the
            // transaction as-is) — same per-item isolation CategorizationRetryJob relies on for its
            // own batch loop, so one bad transaction here can't stop the rest of this identity's set.
            categorize(userId, transactionId);
        }
        return transactionIds.size();
    }

    /**
     * Prefers {@code recipientCanonical} over the raw {@code recipientName} when set (ML strategy
     * phase, 2026-07-20) — same "prefer canonical, fall back to raw" idiom already established in
     * {@code RecurringPaymentDetector#canonicalOrRawName}, so a payee rename/merge (which sets
     * {@code recipientCanonical} immediately, see {@code TransactionService#correctPayeeIdentity})
     * feeds the corrected name into the next prediction instead of the same noisy raw spelling that
     * may have caused the original miscategorization. {@code recipientCanonical} is null until a
     * canonicalization/override event sets it, so this is a no-op for every prediction path until
     * that has happened for the identity in question.
     */
    private static MlPredictionRequest toPredictionRequest(Transaction transaction) {
        String recipientName = transaction.recipientCanonical() != null && !transaction.recipientCanonical().isBlank()
                ? transaction.recipientCanonical()
                : transaction.recipientName();
        return new MlPredictionRequest(
                recipientName,
                transaction.upiId(),
                transaction.bank(),
                transaction.transactionMode(),
                transaction.amount(),
                transaction.note());
    }

    private static MlRetrainCorrection toRetrainCorrection(MlCorrectionRecord record) {
        return new MlRetrainCorrection(
                record.recipientName(),
                record.upiId(),
                record.bank(),
                record.transactionMode(),
                record.amount(),
                record.note(),
                record.categoryId());
    }
}
