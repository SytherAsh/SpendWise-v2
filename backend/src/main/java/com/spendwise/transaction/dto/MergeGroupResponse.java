package com.spendwise.transaction.dto;

import com.spendwise.transaction.MergeSuggestionGroup;

import java.util.List;

public record MergeGroupResponse(
        String anchorName, String anchorUpiId, String anchorCanonicalName, List<MergeCandidateResponse> candidates) {

    public static MergeGroupResponse from(MergeSuggestionGroup group) {
        return new MergeGroupResponse(
                group.anchorName(),
                group.anchorUpiId(),
                group.anchorCanonicalName(),
                group.candidates().stream().map(MergeCandidateResponse::from).toList());
    }
}
