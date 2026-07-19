package com.spendwise.transaction;

import com.spendwise.transaction.dto.MergeDecisionRequest;
import com.spendwise.transaction.dto.MergeQueueResponse;
import com.spendwise.transaction.dto.MergeResolutionResponse;
import com.spendwise.transaction.dto.ResolveMergeQueueRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Merge Payees human-review queue (ML strategy phase, 2026-07-19) — docs/spec/api.md
 * "/payee-merge-queue". A sibling sub-resource of {@link TransactionController} (same package,
 * same precedent as {@link EmiController}/{@link CategoryController}) rather than a new module:
 * it's a distinct resource shape (a review queue, not the transaction list itself), not a
 * different security surface — every route here sits behind the same default user-JWT filter
 * chain, scoped via {@code @AuthenticationPrincipal} like every other user-facing endpoint in
 * this module.
 */
@RestController
@RequestMapping("/api/v1/payee-merge-queue")
public class PayeeMergeQueueController {

    private final TransactionService transactionService;

    public PayeeMergeQueueController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public MergeQueueResponse queue(@AuthenticationPrincipal UUID userId) {
        return MergeQueueResponse.from(transactionService.getMergeQueueSnapshot(userId));
    }

    @PostMapping("/resolve")
    public MergeResolutionResponse resolve(@AuthenticationPrincipal UUID userId, @RequestBody ResolveMergeQueueRequest request) {
        List<UUID> same = new ArrayList<>();
        List<UUID> different = new ArrayList<>();
        for (MergeDecisionRequest decision : request.decisions()) {
            (decision.same() ? same : different).add(decision.suggestionId());
        }
        transactionService.resolveMergeSame(userId, same);
        transactionService.resolveMergeDifferent(userId, different);
        return new MergeResolutionResponse(same.size(), different.size());
    }
}
