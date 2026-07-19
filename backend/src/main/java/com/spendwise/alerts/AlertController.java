package com.spendwise.alerts;

import com.spendwise.alerts.dto.AlertListResponse;
import com.spendwise.alerts.dto.AlertResponse;
import com.spendwise.transaction.dto.EmiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** docs/api.md "/alerts" — owned by the Alerts module. */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    // docs/api.md Pagination section's own example ("GET /api/v1/alerts?limit=20&cursor=...")
    // — not explicitly mandated elsewhere, but the doc's own illustration is the closest thing
    // to a spec default, and 20 is a reasonable page size for a lower-volume list than transactions.
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AlertsService alertsService;
    private final AlertEvaluatorJob alertEvaluatorJob;

    public AlertController(AlertsService alertsService, AlertEvaluatorJob alertEvaluatorJob) {
        this.alertsService = alertsService;
        this.alertEvaluatorJob = alertEvaluatorJob;
    }

    @GetMapping
    public AlertListResponse list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(value = "is_read", required = false) Boolean isRead) {
        AlertPage page = alertsService.list(userId, limit != null ? limit : DEFAULT_PAGE_SIZE, cursor, isRead);
        List<AlertResponse> data = page.data().stream().map(AlertResponse::from).toList();
        return new AlertListResponse(data, page.nextCursor(), page.hasMore());
    }

    @PutMapping("/{id}/read")
    public void markRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        alertsService.markRead(userId, id);
    }

    /**
     * E6-S2-T2 — confirm-as-subscription. Dismiss has no dedicated endpoint: it reuses {@code PUT
     * /alerts/:id/read} directly (docs/api.md "/alerts"), since dismissing a recurring_payment
     * alert needs nothing beyond the existing mark-read behavior.
     */
    @PostMapping("/{id}/confirm")
    public EmiResponse confirm(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return EmiResponse.from(alertsService.confirmRecurringPayment(userId, id));
    }

    /**
     * Manual per-user re-evaluation trigger (Merge Payees feature, 2026-07-19) — called by the
     * frontend right after confirming a payee merge, so recurring-payment detection and budget
     * alerts reflect the corrected identity immediately rather than waiting for the next scheduled
     * {@code AlertEvaluatorJob} run. Injects {@link AlertEvaluatorJob} directly (same package,
     * an intra-module dependency, not a cross-module one) rather than routing through {@link
     * AlertsService}, since {@code AlertEvaluatorJob} already depends on {@code AlertsService} —
     * the reverse direction would be a circular bean dependency.
     */
    @PostMapping("/reevaluate")
    public ResponseEntity<Void> reevaluate(@AuthenticationPrincipal UUID userId) {
        alertEvaluatorJob.runForUser(userId);
        return ResponseEntity.accepted().build();
    }
}
