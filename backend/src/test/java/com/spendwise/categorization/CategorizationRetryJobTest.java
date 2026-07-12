package com.spendwise.categorization;

import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UncategorizedTransactionRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Required test for E4-S3-T3 (invoked directly, not waiting for the real 30-minute schedule):
 * every uncategorized ref found gets re-attempted; a failure in the lookup itself doesn't crash
 * the scheduler thread.
 */
class CategorizationRetryJobTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final CategorizationService categorizationService = mock(CategorizationService.class);
    private final CategorizationRetryJob job = new CategorizationRetryJob(transactionService, categorizationService);

    CategorizationRetryJobTest() {
        given(categorizationService.lowConfidenceThreshold()).willReturn(0.5);
    }

    @Test
    void reCategorizesEveryUncategorizedTransactionFound() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID txnA = UUID.randomUUID();
        UUID txnB = UUID.randomUUID();
        given(transactionService.findAllUncategorized(anyInt(), anyDouble()))
                .willReturn(List.of(new UncategorizedTransactionRef(userA, txnA), new UncategorizedTransactionRef(userB, txnB)));

        job.run();

        verify(categorizationService).categorize(userA, txnA);
        verify(categorizationService).categorize(userB, txnB);
    }

    @Test
    void emptyBacklogCategorizesNothing() {
        given(transactionService.findAllUncategorized(anyInt(), anyDouble())).willReturn(List.of());

        assertThatCode(job::run).doesNotThrowAnyException();
    }

    @Test
    void lookupFailureDoesNotThrow() {
        given(transactionService.findAllUncategorized(anyInt(), anyDouble())).willThrow(new RuntimeException("spendwise_jobs connection lost"));

        assertThatCode(job::run).doesNotThrowAnyException();
    }

    @Test
    void oneItemFailingDoesNotStopTheRest() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID txnA = UUID.randomUUID();
        UUID txnB = UUID.randomUUID();
        given(transactionService.findAllUncategorized(anyInt(), anyDouble()))
                .willReturn(List.of(new UncategorizedTransactionRef(userA, txnA), new UncategorizedTransactionRef(userB, txnB)));
        // categorize() itself never throws per its own contract (E4-S3-T1), but this job's loop
        // must still tolerate it if it somehow did.
        doThrow(new RuntimeException("unexpected")).when(categorizationService).categorize(userA, txnA);

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(categorizationService).categorize(userA, txnA);
        verify(categorizationService).categorize(userB, txnB);
    }
}
