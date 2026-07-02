package com.spendwise.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Required tests for E3-S1-T2 (docs/testing.md Ingest dedup unit tests: primary key check,
 * composite/secondary key check) and E3-S2-T3/T4 manual-entry and category-correction logic.
 */
class TransactionServiceImplTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final TransactionCategoryRepository transactionCategoryRepository = mock(TransactionCategoryRepository.class);
    private final MlCorrectionRepository mlCorrectionRepository = mock(MlCorrectionRepository.class);
    private final TransactionServiceImpl service = new TransactionServiceImpl(
            transactionRepository, categoryRepository, transactionCategoryRepository, mlCorrectionRepository);
    private final UUID userId = UUID.randomUUID();

    @Test
    void secondaryKeyDuplicateIsRejectedWithoutAttemptingInsert() {
        NewTransactionData data = data("swiggy@okicici", "txn_a");
        given(transactionRepository.existsBySecondaryKey(userId, "swiggy@okicici", data.amount(), data.transactionDate()))
                .willReturn(true);

        TransactionInsertOutcome outcome = service.persistFromIngest(userId, data);

        assertThat(outcome).isEqualTo(TransactionInsertOutcome.DUPLICATE);
        verify(transactionRepository, never()).insert(any(), any());
    }

    @Test
    void secondaryKeyCheckIsSkippedWhenUpiIdIsNull() {
        NewTransactionData data = data(null, "txn_b");
        given(transactionRepository.insert(eq(userId), any())).willReturn(sampleTransaction());

        service.persistFromIngest(userId, data);

        verify(transactionRepository, never()).existsBySecondaryKey(any(), any(), any(), any());
        verify(transactionRepository).insert(eq(userId), any());
    }

    @Test
    void primaryKeyDuplicateFromDbConstraintIsCaughtAndReturnsDuplicate() {
        NewTransactionData data = data(null, "txn_c");
        given(transactionRepository.insert(eq(userId), any())).willThrow(new DuplicateKeyException("dup"));

        TransactionInsertOutcome outcome = service.persistFromIngest(userId, data);

        assertThat(outcome).isEqualTo(TransactionInsertOutcome.DUPLICATE);
    }

    @Test
    void newTransactionIsCreatedWithSourceForcedToSms() {
        NewTransactionData data = new NewTransactionData(
                Instant.parse("2025-06-15T14:32:00Z"),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.valueOf(-10),
                null,
                "UPI",
                "DR",
                "txn_d",
                "Swiggy",
                "ICICI",
                null,
                null,
                TransactionSource.MANUAL); // deliberately wrong source in the input — must be overridden
        given(transactionRepository.insert(eq(userId), any())).willReturn(sampleTransaction());

        TransactionInsertOutcome outcome = service.persistFromIngest(userId, data);

        assertThat(outcome).isEqualTo(TransactionInsertOutcome.CREATED);
        org.mockito.ArgumentCaptor<NewTransactionData> captor = org.mockito.ArgumentCaptor.forClass(NewTransactionData.class);
        verify(transactionRepository).insert(eq(userId), captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(TransactionSource.SMS);
    }

    @Test
    void createManualAlwaysUsesServerGeneratedTransactionIdAndManualSource() {
        NewTransactionData data = new NewTransactionData(
                Instant.now(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.valueOf(-10), null, null, "DR", "client-supplied-id",
                null, null, null, null, TransactionSource.SMS);
        given(transactionRepository.insert(eq(userId), any())).willReturn(sampleTransaction());

        service.createManual(userId, data);

        org.mockito.ArgumentCaptor<NewTransactionData> captor = org.mockito.ArgumentCaptor.forClass(NewTransactionData.class);
        verify(transactionRepository).insert(eq(userId), captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(TransactionSource.MANUAL);
        assertThat(captor.getValue().transactionId()).isNotEqualTo("client-supplied-id");
    }

    @Test
    void correctCategoryThrowsNotFoundForMissingTransaction() {
        UUID transactionId = UUID.randomUUID();
        given(transactionRepository.findById(userId, transactionId)).willReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                TransactionNotFoundException.class, () -> service.correctCategory(userId, transactionId, 7));
    }

    @Test
    void correctCategoryThrowsInvalidCategoryForUnknownCategoryId() {
        UUID transactionId = UUID.randomUUID();
        given(transactionRepository.findById(userId, transactionId)).willReturn(Optional.of(sampleTransaction()));
        given(categoryRepository.existsById(999)).willReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                InvalidCategoryException.class, () -> service.correctCategory(userId, transactionId, 999));
    }

    @Test
    void correctCategoryWritesBothTablesWhenChangingCategory() {
        UUID transactionId = UUID.randomUUID();
        given(transactionRepository.findById(userId, transactionId)).willReturn(Optional.of(sampleTransaction()));
        given(categoryRepository.existsById(7)).willReturn(true);
        given(transactionCategoryRepository.findCategoryId(userId, transactionId)).willReturn(Optional.of(3));

        service.correctCategory(userId, transactionId, 7);

        verify(transactionCategoryRepository).upsertUserAssignment(userId, transactionId, 7);
        verify(mlCorrectionRepository).insert(userId, transactionId, 3, 7);
    }

    @Test
    void correctCategoryIsNoOpWhenNewCategoryMatchesExisting() {
        UUID transactionId = UUID.randomUUID();
        given(transactionRepository.findById(userId, transactionId)).willReturn(Optional.of(sampleTransaction()));
        given(categoryRepository.existsById(7)).willReturn(true);
        given(transactionCategoryRepository.findCategoryId(userId, transactionId)).willReturn(Optional.of(7));

        service.correctCategory(userId, transactionId, 7);

        verify(transactionCategoryRepository, never()).upsertUserAssignment(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(mlCorrectionRepository, never()).insert(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private NewTransactionData data(String upiId, String transactionId) {
        return new NewTransactionData(
                Instant.parse("2025-06-15T14:32:00Z"),
                BigDecimal.valueOf(350),
                BigDecimal.ZERO,
                BigDecimal.valueOf(-350),
                null,
                "UPI",
                "DR",
                transactionId,
                "Swiggy",
                "ICICI",
                upiId,
                null,
                TransactionSource.SMS);
    }

    private Transaction sampleTransaction() {
        return new Transaction(
                UUID.randomUUID(),
                userId,
                Instant.now(),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.valueOf(-10),
                null,
                "UPI",
                "DR",
                "txn_x",
                "Swiggy",
                "ICICI",
                "swiggy@okicici",
                null,
                TransactionSource.SMS,
                Instant.now(),
                null,
                null,
                null);
    }
}
