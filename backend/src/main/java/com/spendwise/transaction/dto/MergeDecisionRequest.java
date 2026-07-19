package com.spendwise.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** One decision within {@code POST /payee-merge-queue/resolve}'s body (Merge Payees feature). */
public record MergeDecisionRequest(@JsonProperty("suggestion_id") UUID suggestionId, boolean same) {}
