package com.spendwise.recommendations;

/** Mirrors the {@code recommendations.priority} check constraint (docs/database.md `recommendations` table). */
public enum RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static RecommendationPriority fromDbValue(String value) {
        return RecommendationPriority.valueOf(value.toUpperCase());
    }
}
