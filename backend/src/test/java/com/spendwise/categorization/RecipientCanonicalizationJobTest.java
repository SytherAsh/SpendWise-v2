package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlAmbiguousCandidate;
import com.spendwise.categorization.dto.MlAmbiguousGroup;
import com.spendwise.categorization.dto.MlNormalizeEntry;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.common.db.AdminEventLog;
import com.spendwise.transaction.NewMergeSuggestion;
import com.spendwise.transaction.RecipientCanonicalOverride;
import com.spendwise.transaction.RecipientIdentity;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UnorderedPairKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private final AdminEventLog adminEventLog = mock(AdminEventLog.class);
    private final RecipientCanonicalizationJob job =
            new RecipientCanonicalizationJob(transactionService, categorizationService, adminEventLog);

    @Test
    void runNowDelegatesToRun() {
        given(transactionService.findAllRecipientIdentities()).willReturn(List.of());

        job.runNow();

        verify(transactionService).findAllRecipientIdentities();
    }

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
        // System-wide failure (no single user to scope it to) — surfaced in the admin Logs view.
        verify(adminEventLog)
                .record(eq("canonicalization_failure"), isNull(), eq(Map.of("stage", "identity_lookup", "error", "spendwise_jobs connection lost")));
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
        // userA's failure is scoped to userA specifically, not logged system-wide.
        verify(adminEventLog)
                .record(eq("canonicalization_failure"), eq(userA), eq(Map.of("stage", "canonicalize_user", "error", "FastAPI unavailable")));
    }

    @Test
    void userPinnedOverrideWinsOverTheMlAnswerForTheSameIdentity() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "MR SAMEER SAWANT", "sameer@ok"),
                        new RecipientIdentity(userA, "Uber", null)));
        // ML would cluster the first identity under "SAMEER SAWANT", but the user has pinned it
        // to "Sameer S." — that pin must win. The second identity has no override, so the ML
        // answer ("UBER") is used as normal.
        given(transactionService.findAllCanonicalOverrides())
                .willReturn(List.of(new RecipientCanonicalOverride(userA, "MR SAMEER SAWANT", "sameer@ok", "Sameer S.")));
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(Map.of("0", "SAMEER SAWANT", "1", "UBER")));

        job.run();

        verify(transactionService).updateCanonicalForIdentity(userA, "MR SAMEER SAWANT", "sameer@ok", "Sameer S.");
        verify(transactionService).updateCanonicalForIdentity(userA, "Uber", null, "UBER");
    }

    @Test
    void overrideLookupFailureFallsBackToMlAnswerWithoutCrashing() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(new RecipientIdentity(userA, "Uber", null)));
        given(transactionService.findAllCanonicalOverrides()).willThrow(new RuntimeException("spendwise_jobs connection lost"));
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(Map.of("0", "UBER")));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(transactionService).updateCanonicalForIdentity(userA, "Uber", null, "UBER");
        // System-wide failure (the override lookup isn't scoped to one user either).
        verify(adminEventLog)
                .record(eq("canonicalization_failure"), isNull(), eq(Map.of("stage", "override_lookup", "error", "spendwise_jobs connection lost")));
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

    @Test
    @SuppressWarnings("unchecked")
    void ambiguousGroupsAreRecordedAsNewMergeSuggestions() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "SAMEER SAWANT", null), new RecipientIdentity(userA, "SAMEER", null)));
        given(transactionService.findExistingMergeSuggestionPairs(userA)).willReturn(Set.of());
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(
                        Map.of("0", "SAMEER SAWANT", "1", "SAMEER"),
                        List.of(new MlAmbiguousGroup(
                                "0", "SAMEER SAWANT", List.of(new MlAmbiguousCandidate("1", "SAMEER", 100, "prefix_ambiguous"))))));

        job.run();

        ArgumentCaptor<List<NewMergeSuggestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionService).recordMergeSuggestions(eq(userA), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        NewMergeSuggestion suggestion = captor.getValue().get(0);
        assertThat(suggestion.anchorName()).isEqualTo("SAMEER SAWANT");
        assertThat(suggestion.anchorCanonicalName()).isEqualTo("SAMEER SAWANT");
        assertThat(suggestion.candidateName()).isEqualTo("SAMEER");
        assertThat(suggestion.score()).isEqualTo(100);
        assertThat(suggestion.reason()).isEqualTo("prefix_ambiguous");
    }

    @Test
    void alreadySuggestedPairIsNeverReSuggestedRegardlessOfAnchorCandidateOrder() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "SAMEER SAWANT", null), new RecipientIdentity(userA, "SAMEER", null)));
        // Already known with anchor/candidate roles swapped from what this run would produce --
        // the algorithm can flip which identity it calls "anchor" between resweeps as frequencies
        // shift, so the dedup check must be direction-independent.
        given(transactionService.findExistingMergeSuggestionPairs(userA))
                .willReturn(Set.of(UnorderedPairKey.of("SAMEER", null, "SAMEER SAWANT", null)));
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(
                        Map.of("0", "SAMEER SAWANT", "1", "SAMEER"),
                        List.of(new MlAmbiguousGroup(
                                "0", "SAMEER SAWANT", List.of(new MlAmbiguousCandidate("1", "SAMEER", 100, "prefix_ambiguous"))))));

        job.run();

        verify(transactionService, never()).recordMergeSuggestions(any(), any());
    }

    @Test
    void candidateIdentityWithAnExistingOverrideIsNeverSuggested() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "SAMEER SAWANT", null), new RecipientIdentity(userA, "SAMEER", null)));
        // "SAMEER" already has its own override pinned (resolved against some other anchor
        // already) -- must never be re-suggested against a different anchor too.
        given(transactionService.findAllCanonicalOverrides())
                .willReturn(List.of(new RecipientCanonicalOverride(userA, "SAMEER", null, "SAMEER BALIRAM SAWA")));
        given(transactionService.findExistingMergeSuggestionPairs(userA)).willReturn(Set.of());
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(
                        Map.of("0", "SAMEER SAWANT", "1", "SAMEER"),
                        List.of(new MlAmbiguousGroup(
                                "0", "SAMEER SAWANT", List.of(new MlAmbiguousCandidate("1", "SAMEER", 100, "prefix_ambiguous"))))));

        job.run();

        verify(transactionService, never()).recordMergeSuggestions(any(), any());
    }

    @Test
    void mergeSuggestionLookupFailureDoesNotCrashTheSweepOrUndoTheCanonicalWrite() {
        UUID userA = UUID.randomUUID();
        given(transactionService.findAllRecipientIdentities())
                .willReturn(List.of(
                        new RecipientIdentity(userA, "SAMEER SAWANT", null), new RecipientIdentity(userA, "SAMEER", null)));
        given(transactionService.findExistingMergeSuggestionPairs(userA))
                .willThrow(new RuntimeException("spendwise_jobs connection lost"));
        given(categorizationService.normalizeRecipients(any()))
                .willReturn(new MlNormalizeRecipientsResponse(
                        Map.of("0", "SAMEER SAWANT", "1", "SAMEER"),
                        List.of(new MlAmbiguousGroup(
                                "0", "SAMEER SAWANT", List.of(new MlAmbiguousCandidate("1", "SAMEER", 100, "prefix_ambiguous"))))));

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(transactionService).updateCanonicalForIdentity(userA, "SAMEER SAWANT", null, "SAMEER SAWANT");
        verify(transactionService).updateCanonicalForIdentity(userA, "SAMEER", null, "SAMEER");
        verify(transactionService, never()).recordMergeSuggestions(any(), any());
        verify(adminEventLog)
                .record(
                        eq("canonicalization_failure"),
                        eq(userA),
                        eq(Map.of("stage", "merge_suggestions", "error", "spendwise_jobs connection lost")));
    }
}
