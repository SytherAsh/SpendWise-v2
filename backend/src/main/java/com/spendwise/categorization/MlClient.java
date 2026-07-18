package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlEvaluationResponse;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.spendwise.categorization.dto.MlPredictionRequest;
import com.spendwise.categorization.dto.MlPredictionResponse;
import com.spendwise.categorization.dto.MlRecurringPredictionRequest;
import com.spendwise.categorization.dto.MlRecurringPredictionResponse;
import com.spendwise.categorization.dto.MlRecurringRetrainRequest;
import com.spendwise.categorization.dto.MlRecurringRetrainResponse;
import com.spendwise.categorization.dto.MlRetrainRequest;
import com.spendwise.categorization.dto.MlRetrainResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The only class in the whole system permitted to call the FastAPI ML service (E4-S3-T5;
 * CLAUDE.md "FastAPI is called only from the Categorization module"). Sets {@code
 * X-Internal-Key} on every outbound call, per docs/architecture.md's "Internal access only" note.
 * No other module may inject this class — see {@code CategorizationBoundaryTest}.
 */
@Component
public class MlClient {

    // Fast-fail connect timeout, not Spring's OS-default (which can be 60s+): a FastAPI outage
    // must fail fast enough that E4-S3-T1's "never crashes the ingest flow" doesn't mean "hangs
    // the ingest flow" instead — /predict is called synchronously per ingested item (E4-S3-T2).
    // Read timeout is generous (once connected, FastAPI is up and actually working) — /retrain
    // and /evaluate both fit a fresh RandomForest and can legitimately take tens of seconds.
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int READ_TIMEOUT_MILLIS = 120_000;

    private final RestClient restClient;
    private final String internalKey;

    public MlClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.ml.base-url}") String baseUrl,
            @Value("${app.ml.internal-key}") String internalKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalKey = internalKey;
    }

    /** @throws org.springframework.web.client.RestClientException on network failure or non-2xx response */
    public MlPredictionResponse predict(MlPredictionRequest request) {
        return restClient
                .post()
                .uri("/predict")
                .header("X-Internal-Key", internalKey)
                .body(request)
                .retrieve()
                .body(MlPredictionResponse.class);
    }

    /** @throws org.springframework.web.client.RestClientException on network failure or non-2xx response */
    public MlRetrainResponse retrain(MlRetrainRequest request) {
        return restClient
                .post()
                .uri("/retrain")
                .header("X-Internal-Key", internalKey)
                .body(request)
                .retrieve()
                .body(MlRetrainResponse.class);
    }

    /** @throws org.springframework.web.client.RestClientException on network failure or non-2xx response */
    public MlEvaluationResponse evaluate() {
        return restClient.get().uri("/evaluate").header("X-Internal-Key", internalKey).retrieve().body(MlEvaluationResponse.class);
    }

    /**
     * Recurring-payment classifier (ML strategy phase, 2026-07-11) — same internal-only contract
     * as {@link #predict}, called from {@link CategorizationServiceImpl#predictRecurring}, which is
     * in turn the only path {@code Alerts} may reach this service through (CategorizationService is
     * the ML gateway; see docs/spec/decisions.md).
     *
     * @throws org.springframework.web.client.RestClientException on network failure or non-2xx response
     */
    public MlRecurringPredictionResponse predictRecurring(MlRecurringPredictionRequest request) {
        return restClient
                .post()
                .uri("/predict-recurring")
                .header("X-Internal-Key", internalKey)
                .body(request)
                .retrieve()
                .body(MlRecurringPredictionResponse.class);
    }

    /** @throws org.springframework.web.client.RestClientException on network failure or non-2xx response */
    public MlRecurringRetrainResponse retrainRecurring(MlRecurringRetrainRequest request) {
        return restClient
                .post()
                .uri("/retrain-recurring")
                .header("X-Internal-Key", internalKey)
                .body(request)
                .retrieve()
                .body(MlRecurringRetrainResponse.class);
    }

    /**
     * Recipient-name canonicalization (ML strategy phase, 2026-07-13) — same internal-only
     * contract as {@link #predict}, called from {@link CategorizationServiceImpl#normalizeRecipients}
     * (in turn the only path {@code RecipientCanonicalizationJob} reaches this service through).
     * Unlike {@link #predict}, one call carries a whole user's recipient set rather than a single
     * transaction; the clustering runs across all of it at once.
     *
     * @throws org.springframework.web.client.RestClientException on network failure or non-2xx response
     */
    public MlNormalizeRecipientsResponse normalizeRecipients(MlNormalizeRecipientsRequest request) {
        return restClient
                .post()
                .uri("/normalize-recipients")
                .header("X-Internal-Key", internalKey)
                .body(request)
                .retrieve()
                .body(MlNormalizeRecipientsResponse.class);
    }
}
