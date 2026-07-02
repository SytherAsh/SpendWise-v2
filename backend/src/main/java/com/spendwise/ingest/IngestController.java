package com.spendwise.ingest;

import com.spendwise.ingest.dto.IngestBatchRequest;
import com.spendwise.ingest.dto.IngestBatchResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Guarded by {@link DeviceApiKeyAuthFilter} (composed with {@code UserJwtAuthFilter} in {@link IngestSecurityConfig}). */
@RestController
@RequestMapping("/api/v1/ingest/transactions")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public ResponseEntity<IngestBatchResponse> ingest(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody IngestBatchRequest request) {
        IngestOutcome outcome = ingestService.ingestBatch(userId, request.transactions());
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }
}
