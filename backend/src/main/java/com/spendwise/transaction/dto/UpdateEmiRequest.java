package com.spendwise.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** {@code PUT /emis/:id} — full replacement of label/amount/due_day (E3-S3-T2). */
public record UpdateEmiRequest(
        @NotBlank String label,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Min(1) @Max(31) Integer dueDay) {}
