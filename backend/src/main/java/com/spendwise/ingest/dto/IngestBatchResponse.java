package com.spendwise.ingest.dto;

import java.util.List;

public record IngestBatchResponse(List<IngestItemResult> results) {}
