package com.spendwise.transaction;

/** Result of {@link TransactionService#persistFromIngest} — a duplicate is a normal outcome, never an exception. */
public enum TransactionInsertOutcome {
    CREATED,
    DUPLICATE
}
