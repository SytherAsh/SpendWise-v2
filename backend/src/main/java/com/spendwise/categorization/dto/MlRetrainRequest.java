package com.spendwise.categorization.dto;

import java.util.List;

/** Mirrors the FastAPI {@code POST /retrain} request body (ml/api/schemas.py RetrainRequest). */
public record MlRetrainRequest(List<MlRetrainCorrection> corrections) {}
