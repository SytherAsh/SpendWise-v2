package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlEvaluationResponse;
import com.spendwise.categorization.dto.MlPredictionRequest;
import com.spendwise.categorization.dto.MlPredictionResponse;
import com.spendwise.categorization.dto.MlRecurringPredictionRequest;
import com.spendwise.categorization.dto.MlRecurringPredictionResponse;
import com.spendwise.categorization.dto.MlRetrainCorrection;
import com.spendwise.categorization.dto.MlRetrainRequest;
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
    private final double lowConfidenceThreshold;

    public CategorizationServiceImpl(
            MlClient mlClient,
            TransactionService transactionService,
            @Value("${app.ml.low-confidence-threshold}") double lowConfidenceThreshold) {
        this.mlClient = mlClient;
        this.transactionService = transactionService;
        this.lowConfidenceThreshold = lowConfidenceThreshold;
    }

    @Override
    public void categorize(UUID userId, UUID transactionId) {
        try {
            Transaction transaction = transactionService.getById(userId, transactionId);
            MlPredictionResponse response = mlClient.predict(toPredictionRequest(transaction));

            if (response.confidence() < lowConfidenceThreshold) {
                log.info(
                        "Low-confidence prediction ({}) for transaction {} — leaving uncategorized for retry",
                        response.confidence(),
                        transactionId);
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

    private static MlPredictionRequest toPredictionRequest(Transaction transaction) {
        return new MlPredictionRequest(
                transaction.recipientName(),
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
