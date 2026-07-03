package com.spendwise.alerts;

/** Mirrors the {@code alert_type} Postgres enum (docs/database.md `alerts` table). */
public enum AlertType {
    MID_MONTH_BUDGET,
    CATEGORY_OVERSPEND,
    CATEGORY_APPROACHING_LIMIT,
    // Reserved for Epic 6 (recurring payment detection) — not produced by this epic's evaluator.
    RECURRING_PAYMENT;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static AlertType fromDbValue(String value) {
        return AlertType.valueOf(value.toUpperCase());
    }
}
