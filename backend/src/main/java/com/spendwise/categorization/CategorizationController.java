package com.spendwise.categorization;

import com.spendwise.categorization.dto.RecategorizeIdentityRequest;
import com.spendwise.categorization.dto.RecategorizeIdentityResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * First user-facing controller for the Categorization module (ADR-020, ML strategy phase,
 * 2026-07-20) — every other entry point into this module is either internal (Ingest calling
 * {@link CategorizationService#categorize}) or Admin-triggered ({@code AdminController}). Same
 * default user-JWT filter chain as every other user-facing endpoint in this app; scoped via
 * {@code @AuthenticationPrincipal} like {@code PayeeMergeQueueController}.
 */
@RestController
@RequestMapping("/api/v1/categorization")
public class CategorizationController {

    private final CategorizationService categorizationService;

    public CategorizationController(CategorizationService categorizationService) {
        this.categorizationService = categorizationService;
    }

    /**
     * Fired by the frontend as a second, independent request after a payee rename succeeds
     * (Merge Payees' "Confirm & next", or the manual payee-rename control) — never triggered
     * server-side by the Transaction module itself, which would create a circular module
     * dependency; see ADR-020 in docs/spec/decisions.md.
     */
    @PostMapping("/recategorize")
    public RecategorizeIdentityResponse recategorize(
            @AuthenticationPrincipal UUID userId, @RequestBody RecategorizeIdentityRequest request) {
        int recategorized = categorizationService.recategorizeIdentity(userId, request.recipientName(), request.upiId());
        return new RecategorizeIdentityResponse(recategorized);
    }
}
