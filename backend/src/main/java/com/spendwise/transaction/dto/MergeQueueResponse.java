package com.spendwise.transaction.dto;

import com.spendwise.transaction.MergeQueueSnapshot;

/** {@code nextGroup} is {@code null} once the user has cleared every pending suggestion. */
public record MergeQueueResponse(MergeGroupResponse nextGroup, int remainingGroupCount) {

    public static MergeQueueResponse from(MergeQueueSnapshot snapshot) {
        return new MergeQueueResponse(
                snapshot.nextGroup() == null ? null : MergeGroupResponse.from(snapshot.nextGroup()), snapshot.remainingGroupCount());
    }
}
