package com.spendwise.ingest;

import com.spendwise.categorization.CategorizationService;
import com.spendwise.ingest.dto.IngestTransactionItem;
import com.spendwise.transaction.NewTransactionData;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.TransactionSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Required tests for E3-S1-T2 (docs/testing.md Ingest dedup unit tests): duplicate single item
 * -> per-item 409 and overall 409; mixed-batch partial failure -> 2 of 3 persist with correct
 * per-item statuses and overall 200 (since at least one item was created). Also E4-S3-T2: every
 * created item triggers Categorization immediately, duplicates never do.
 */
class IngestServiceTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final CategorizationService categorizationService = mock(CategorizationService.class);
    private final IngestService ingestService = new IngestService(transactionService, categorizationService);
    private final UUID userId = UUID.randomUUID();

    @Test
    void duplicateSingleItemReturns409OverallAnd409PerItem() {
        IngestTransactionItem item = item("txn_dup");
        given(transactionService.persistFromIngest(eq(userId), any(NewTransactionData.class))).willReturn(Optional.empty());

        IngestOutcome outcome = ingestService.ingestBatch(userId, List.of(item));

        assertThat(outcome.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(outcome.body().results()).hasSize(1);
        assertThat(outcome.body().results().get(0).transactionId()).isEqualTo("txn_dup");
        assertThat(outcome.body().results().get(0).status()).isEqualTo(409);
        verify(categorizationService, never()).categorize(any(), any());
    }

    @Test
    void mixedBatchPartialFailureReturns2Of3CreatedWithCorrectPerItemStatusesAndOverall200() {
        IngestTransactionItem first = item("txn_1");
        IngestTransactionItem duplicate = item("txn_2");
        IngestTransactionItem third = item("txn_3");
        UUID firstId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        given(transactionService.persistFromIngest(eq(userId), eq(toData(first))))
                .willReturn(Optional.of(sampleTransaction(firstId)));
        given(transactionService.persistFromIngest(eq(userId), eq(toData(duplicate)))).willReturn(Optional.empty());
        given(transactionService.persistFromIngest(eq(userId), eq(toData(third))))
                .willReturn(Optional.of(sampleTransaction(thirdId)));

        IngestOutcome outcome = ingestService.ingestBatch(userId, List.of(first, duplicate, third));

        assertThat(outcome.status()).isEqualTo(HttpStatus.OK);
        assertThat(outcome.body().results()).hasSize(3);
        assertThat(outcome.body().results().get(0).status()).isEqualTo(201);
        assertThat(outcome.body().results().get(1).status()).isEqualTo(409);
        assertThat(outcome.body().results().get(2).status()).isEqualTo(201);
        verify(categorizationService).categorize(userId, firstId);
        verify(categorizationService).categorize(userId, thirdId);
        verify(categorizationService, times(2)).categorize(any(), any());
    }

    @Test
    void allCreatedReturnsOverall200() {
        IngestTransactionItem item = item("txn_new");
        UUID createdId = UUID.randomUUID();
        given(transactionService.persistFromIngest(eq(userId), any(NewTransactionData.class)))
                .willReturn(Optional.of(sampleTransaction(createdId)));

        IngestOutcome outcome = ingestService.ingestBatch(userId, List.of(item));

        assertThat(outcome.status()).isEqualTo(HttpStatus.OK);
        assertThat(outcome.body().results().get(0).status()).isEqualTo(201);
        verify(categorizationService).categorize(userId, createdId);
    }

    @Test
    void categorizationFailureDoesNotAbortTheBatch() {
        IngestTransactionItem item = item("txn_resilient");
        UUID createdId = UUID.randomUUID();
        given(transactionService.persistFromIngest(eq(userId), any(NewTransactionData.class)))
                .willReturn(Optional.of(sampleTransaction(createdId)));
        org.mockito.Mockito.doThrow(new RuntimeException("FastAPI unreachable"))
                .when(categorizationService)
                .categorize(any(), any());

        IngestOutcome outcome = ingestService.ingestBatch(userId, List.of(item));

        // CategorizationService's own contract (E4-S3-T1) is to never throw, but if it somehow
        // did, the ingest item itself must still be reported as created, not a 500 — the DB
        // write already succeeded.
        assertThat(outcome.body().results().get(0).status()).isEqualTo(201);
    }

    private static Transaction sampleTransaction(UUID id) {
        return new Transaction(
                id,
                UUID.randomUUID(),
                Instant.parse("2025-06-15T14:32:00Z"),
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
                Instant.parse("2025-06-15T14:33:00Z"),
                null,
                null,
                null);
    }

    private static IngestTransactionItem item(String transactionId) {
        return new IngestTransactionItem(
                Instant.parse("2025-06-15T14:32:00Z"),
                BigDecimal.valueOf(350.0),
                BigDecimal.ZERO,
                BigDecimal.valueOf(-350.0),
                null,
                "DR",
                transactionId,
                "Swiggy",
                "swiggy@okicici",
                "ICICI",
                "UPI",
                null,
                "sms");
    }

    // Matches IngestService.toNewTransactionData's mapping exactly, so eq(...) matchers on
    // NewTransactionData work without needing a custom ArgumentMatcher.
    private static NewTransactionData toData(IngestTransactionItem item) {
        return new NewTransactionData(
                item.transactionDate(),
                item.debit(),
                item.credit(),
                item.amount(),
                item.balance(),
                item.transactionMode(),
                item.drCrIndicator(),
                item.transactionId(),
                item.recipientName(),
                item.bank(),
                item.upiId(),
                item.note(),
                com.spendwise.transaction.TransactionSource.SMS);
    }
}
