package com.spendwise.transaction;

/** Mirrors the {@code transaction_source} Postgres enum (docs/database.md `transactions` table). */
public enum TransactionSource {
    SMS,
    BANK_STATEMENT,
    MANUAL;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static TransactionSource fromDbValue(String value) {
        return TransactionSource.valueOf(value.toUpperCase());
    }
}
