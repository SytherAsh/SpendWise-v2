package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Matches docs/spec/api.md "POST /categorization/recategorize — Request" (ADR-020):
 * {@code {"recipient_name": "Sameer Sawant", "upi_id": "sameer@okhdfcbank"}}. Both fields are
 * nullable, same as every other (recipient_name, upi_id) identity in this codebase (e.g. {@code
 * TransactionService#correctPayeeIdentity}) — an identity only needs one of the two to be usable
 * (a UPI-only transaction can have a null recipient_name, and vice versa). */
public record RecategorizeIdentityRequest(@JsonProperty("recipient_name") String recipientName, @JsonProperty("upi_id") String upiId) {}
