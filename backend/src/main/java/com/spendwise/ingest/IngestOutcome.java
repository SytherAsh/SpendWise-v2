package com.spendwise.ingest;

import com.spendwise.ingest.dto.IngestBatchResponse;
import org.springframework.http.HttpStatus;

/** Overall envelope status: 409 only when every item in the batch was a duplicate; 200 otherwise. */
public record IngestOutcome(IngestBatchResponse body, HttpStatus status) {}
