package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlPredictionRequest;
import com.spendwise.categorization.dto.MlPredictionResponse;
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

    // Short timeouts, not Spring's OS-default (which can be 60s+): a FastAPI outage must fail
    // fast enough that E4-S3-T1's "never crashes the ingest flow" doesn't mean "hangs the ingest
    // flow" instead — every ingested item calls this synchronously (E4-S3-T2).
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;

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
}
