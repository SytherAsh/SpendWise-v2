package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlEvaluationResponse;
import com.spendwise.categorization.dto.MlPredictionRequest;
import com.spendwise.categorization.dto.MlPredictionResponse;
import com.spendwise.categorization.dto.MlRecurringPredictionRequest;
import com.spendwise.categorization.dto.MlRecurringPredictionResponse;
import com.spendwise.categorization.dto.MlRetrainRequest;
import com.spendwise.categorization.dto.MlRetrainResponse;
import com.spendwise.transaction.MlCorrectionRecord;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.TransactionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Required tests for E4-S3-T1 (docs/testing.md "Categorization: calling ML service, handling
 * low-confidence responses"): success path writes the transaction_categories row; a failed or
 * low-confidence call does not throw uncaught and leaves the transaction uncategorized.
 */
class CategorizationServiceImplTest {

    private final MlClient mlClient = mock(MlClient.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final UUID userId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    private final CategorizationServiceImpl service = new CategorizationServiceImpl(mlClient, transactionService, 0.5);

    @Test
    void successfulPredictWritesTransactionCategoriesRow() {
        given(transactionService.getById(userId, transactionId)).willReturn(transaction());
        given(mlClient.predict(any(MlPredictionRequest.class))).willReturn(new MlPredictionResponse(7, "Food / Dine Out", 0.94));

        service.categorize(userId, transactionId);

        verify(transactionService).assignMlCategory(userId, transactionId, 7, 0.94);
    }

    @Test
    void predictRequestIsBuiltFromTransactionFields() {
        given(transactionService.getById(userId, transactionId)).willReturn(transaction());
        given(mlClient.predict(any(MlPredictionRequest.class))).willReturn(new MlPredictionResponse(7, "Food / Dine Out", 0.94));

        service.categorize(userId, transactionId);

        verify(mlClient)
                .predict(
                        eq(
                                new MlPredictionRequest(
                                        "Swiggy", "swiggy@okicici", "ICICI", "UPI", BigDecimal.valueOf(-350.0), null)));
    }

    @Test
    void lowConfidencePredictionLeavesTransactionUncategorized() {
        given(transactionService.getById(userId, transactionId)).willReturn(transaction());
        given(mlClient.predict(any(MlPredictionRequest.class))).willReturn(new MlPredictionResponse(6, "Miscellaneous", 0.2));

        service.categorize(userId, transactionId);

        verify(transactionService, never()).assignMlCategory(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void confidenceExactlyAtThresholdIsAccepted() {
        given(transactionService.getById(userId, transactionId)).willReturn(transaction());
        given(mlClient.predict(any(MlPredictionRequest.class))).willReturn(new MlPredictionResponse(6, "Miscellaneous", 0.5));

        service.categorize(userId, transactionId);

        verify(transactionService).assignMlCategory(userId, transactionId, 6, 0.5);
    }

    @Test
    void mlClientFailureDoesNotThrowAndLeavesTransactionUncategorized() {
        given(transactionService.getById(userId, transactionId)).willReturn(transaction());
        when(mlClient.predict(any(MlPredictionRequest.class))).thenThrow(new RuntimeException("FastAPI unreachable"));

        assertThatCode(() -> service.categorize(userId, transactionId)).doesNotThrowAnyException();

        verify(transactionService, never()).assignMlCategory(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void unknownTransactionDoesNotThrow() {
        given(transactionService.getById(userId, transactionId)).willThrow(new com.spendwise.transaction.TransactionNotFoundException());

        assertThatCode(() -> service.categorize(userId, transactionId)).doesNotThrowAnyException();
    }

    @Test
    void triggerRetrainSendsAllCorrectionsToFastApi() {
        given(transactionService.findAllCorrections())
                .willReturn(List.of(new MlCorrectionRecord("Cult Gym", "cult@okhdfc", "HDFC", "UPI", BigDecimal.valueOf(-800.0), null, 3)));
        given(mlClient.retrain(any(MlRetrainRequest.class))).willReturn(new MlRetrainResponse("success", 1811));

        service.triggerRetrain();

        verify(mlClient)
                .retrain(
                        eq(
                                new MlRetrainRequest(
                                        List.of(
                                                new com.spendwise.categorization.dto.MlRetrainCorrection(
                                                        "Cult Gym", "cult@okhdfc", "HDFC", "UPI", BigDecimal.valueOf(-800.0), null, 3)))));
    }

    @Test
    void triggerRetrainPropagatesMlClientFailure() {
        given(transactionService.findAllCorrections()).willReturn(List.of());
        when(mlClient.retrain(any(MlRetrainRequest.class))).thenThrow(new RuntimeException("FastAPI unreachable"));

        assertThatThrownBy(service::triggerRetrain).isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAccuracyMetricsReturnsMlClientEvaluateResult() {
        MlEvaluationResponse response = new MlEvaluationResponse(
                "2026-07-02T00:00:00Z", 362, 0.917, List.of(), List.of(), List.of(), new com.spendwise.categorization.dto.MlConfidenceDistribution(0.8, 0.9, 0.3, 1.0), "reports/evaluation_1.json");
        given(mlClient.evaluate()).willReturn(response);

        MlEvaluationResponse result = service.getAccuracyMetrics();

        assertThat(result).isEqualTo(response);
    }

    @Test
    void predictRecurringReturnsMlClientResponseUnchanged() {
        MlRecurringPredictionRequest request = new MlRecurringPredictionRequest(3, 30.0, 0.05, 199.0, 0.02, 60.0, 2.0);
        MlRecurringPredictionResponse response = new MlRecurringPredictionResponse(true, 0.92, "monthly");
        given(mlClient.predictRecurring(request)).willReturn(response);

        MlRecurringPredictionResponse result = service.predictRecurring(request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void predictRecurringPropagatesMlClientFailure() {
        MlRecurringPredictionRequest request = new MlRecurringPredictionRequest(3, 30.0, 0.05, 199.0, 0.02, 60.0, 2.0);
        when(mlClient.predictRecurring(request)).thenThrow(new RuntimeException("FastAPI unreachable"));

        assertThatThrownBy(() -> service.predictRecurring(request)).isInstanceOf(RuntimeException.class);
    }

    private Transaction transaction() {
        return new Transaction(
                transactionId,
                userId,
                Instant.parse("2026-06-15T14:32:00Z"),
                BigDecimal.valueOf(350.0),
                BigDecimal.ZERO,
                BigDecimal.valueOf(-350.0),
                null,
                "UPI",
                "DR",
                "txn_1",
                "Swiggy",
                "ICICI",
                "swiggy@okicici",
                null,
                TransactionSource.SMS,
                Instant.parse("2026-06-15T14:33:00Z"),
                null,
                null,
                null);
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static double anyDouble() {
        return org.mockito.ArgumentMatchers.anyDouble();
    }
}
