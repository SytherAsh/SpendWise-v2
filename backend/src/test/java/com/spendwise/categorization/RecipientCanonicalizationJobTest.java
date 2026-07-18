package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlNormalizeEntry;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.transaction.RecipientIdentity;
import com.spendwise.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the weekly recipient-canonicalization job (ML strategy phase, 2026-07-13),
 * invoked directly rather than waiting on the real schedule. Covers: grouping identities by user
 * and calling the ML gateway once per user (never mixing users), writing each returned canonical
 * name back to the matching identity, and isolating failures so one bad user/lookup never crashes
 * the sweep.
 */
class RecipientCanonicalizationJobTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final CategorizationService categorizationService = mock(CategorizationService.class);
    private final RecipientCanonicalizationJob job = new RecipientCanonicalizationJob(transactionService, categorizationService);

    @Test
    void callsMlGatewayOncePerUserAndWritesCanonicalNamesBack() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "SWIGGY", "swiggy@ok"),
                        new RecipientIdentity(userA, "Swiggy Bangalore", "swiggy@ok"),
                        new RecipientIdentity(userB, "Uber", null)));
        // userA's two identities both canonicalize to "SWIGGY" (keys "0" and "1"); userB's single
        // identity (key "0") to "UBER".
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(Map.of("0", "SWIGGY", "1", "SWIGGY")))
                .willReturn(new MlNormalizeRecipientsResponse(Map.of("0", "UBER")));

        job.run();

        // One gateway call per user, never a combined call.
        ArgumentCaptor<MlNormalizeRecipientsRequest> requestCaptor = ArgumentCaptor.forClass(MlNormalizeRecipientsRequest.class);
        verify(categorizationService, times(2)).normalizeRecipients(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).entries()).extracting(MlNormalizeEntry::recipientName)
                .containsExactly("SWIGGY", "Swiggy Bangalore");
        assertThat(requestCaptor.getAllValues().get(1).entries()).extracting(MlNormalizeEntry::recipientName)
                .containsExactly("Uber");

        // Each identity's canonical name written back to its exact (recipient_name, upi_id) tuple.
        verify(transactionService).updateCanonicalForIdentity(userA, "SWIGGY", "swiggy@ok", "SWIGGY");
        verify(transactionService).updateCanonicalForIdentity(userA, "Swiggy Bangalore", "swiggy@ok", "SWIGGY");
        verify(transactionService).updateCanonicalForIdentity(userB, "Uber", null, "UBER");
    }

    @Test
    void identityLookupFailureDoesNotThrow() {
        given(transactionService.findAllRecipientIdentities()).willThrow(new RuntimeException("spendwise_jobs connection lost"));

        assertThatCode(job::run).doesNotThrowAnyException();
        verify(categorizationService, never()).normalizeRecipients(any());
    }

    @Test
    void oneUserFailingDoesNotStopTheRest() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "Uber", null),
                        new RecipientIdentity(userB, "Ola", null)));
        given(categorizationService.normalizeRecipients(any()))
                .willThrow(new RuntimeException("FastAPI unavailable"))
                .willReturn(new MlNormalizeRecipientsResponse(Map.of("0", "OLA")));

        assertThatCode(job::run).doesNotThrowAnyException();

        // userB still processed despite userA's ML call throwing.
        verify(transactionService).updateCanonicalForIdentity(userB, "Ola", null, "OLA");
    }

    @Test
    void blankOrMissingCanonicalNameIsNotWritten() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "Uber", null),
                        new RecipientIdentity(userA, "Ola", null)));
        // Key "0" comes back blank, key "1" missing entirely — neither should be written.
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(Map.of("0", "  ")));

        job.run();

        verify(transactionService, never()).updateCanonicalForIdentity(eq(userA), any(), any(), any());
    }
}
