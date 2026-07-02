package com.spendwise.ingest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record IngestBatchRequest(@NotNull List<@Valid IngestTransactionItem> transactions) {}
