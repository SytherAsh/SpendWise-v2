package com.spendwise.alerts;

/** Mirrors the {@code alerts.priority} check constraint (docs/database.md `alerts` table). */
public enum AlertPriority {
    HIGH,
    MEDIUM,
    LOW;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static AlertPriority fromDbValue(String value) {
        return AlertPriority.valueOf(value.toUpperCase());
    }
}
