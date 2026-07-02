package com.spendwise.transaction.dto;

import java.util.UUID;

public record CategoryCorrectionResponse(UUID transactionId, Integer categoryId) {}
