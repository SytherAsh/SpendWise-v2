package com.spendwise.transaction.dto;

import com.spendwise.transaction.RecipientMergeSuggestion;

import java.util.UUID;

public record MergeCandidateResponse(UUID suggestionId, String candidateName, String candidateUpiId, int score, String reason) {

    public static MergeCandidateResponse from(RecipientMergeSuggestion suggestion) {
        return new MergeCandidateResponse(
                suggestion.id(), suggestion.candidateName(), suggestion.candidateUpiId(), suggestion.score(), suggestion.reason());
    }
}
