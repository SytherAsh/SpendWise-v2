package com.spendwise.categorization.dto;

import java.util.List;

/** Mirrors the FastAPI {@code POST /retrain-recurring} request body (ml/api/schemas.py RecurringRetrainRequest). */
public record MlRecurringRetrainRequest(List<MlRecurringCorrection> corrections) {}
