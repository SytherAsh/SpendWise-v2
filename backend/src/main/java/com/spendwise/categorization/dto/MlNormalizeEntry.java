package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in a {@code POST /normalize-recipients} request (ml/api/schemas.py NormalizeEntry).
 * {@code key} is a caller-defined identifier echoed back unchanged in the response — the backend
 * uses one synthetic key per distinct (recipient_name, upi_id) pair so the whole clustering runs
 * on the deduplicated set, not once per transaction.
 */
public record MlNormalizeEntry(
        String key,
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("upi_id") String upiId) {}
