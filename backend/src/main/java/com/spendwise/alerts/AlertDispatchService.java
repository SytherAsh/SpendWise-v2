package com.spendwise.alerts;

/**
 * Notification dispatch (E5-S3) — push via FCM and email via SMTP, per docs/requirements.md's
 * priority/delivery table. Callers (the Alerts evaluator job) only invoke this for {@code
 * priority = HIGH} alerts; medium/low priority never dispatches beyond the {@code alerts} row
 * itself. Implementations must never throw — a failed send is logged and leaves {@code
 * delivered_at} null, exactly like every other best-effort background-job dependency in this
 * codebase (e.g. {@code CategorizationService#categorize}).
 */
public interface AlertDispatchService {

    void dispatch(Alert alert);
}
