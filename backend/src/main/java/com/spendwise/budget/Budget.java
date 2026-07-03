package com.spendwise.budget;

import java.math.BigDecimal;
import java.util.UUID;

public record Budget(UUID id, UUID userId, int categoryId, BigDecimal monthlyLimit, int month, int year) {}
