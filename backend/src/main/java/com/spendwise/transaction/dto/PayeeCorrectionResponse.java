package com.spendwise.transaction.dto;

import java.util.UUID;

public record PayeeCorrectionResponse(UUID transactionId, String canonicalName) {}
