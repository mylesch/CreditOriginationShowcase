package com.cos.orchestration.client;

import com.cos.domain.model.Application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calls the COS Decision Rules Service's {@code POST /v1/applications/evaluate} (Process
 * Workbook, "Call Decision Rules Service (DRS)" tab). DRS is a COS-internal service, not
 * documented in the Integration Workbook, so there is no bearer-token acquisition to model here.
 */
@Component
public class DrsClient {

    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), Set.of(500, 501, 502, 503, 504));

    private final RestClient restClient;
    private final RetryingCaller retryingCaller;

    public DrsClient(@Value("${cos.orchestration.drs.base-url}") String baseUrl, RetryingCaller retryingCaller) {
        this.restClient = RestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(15));
        this.retryingCaller = retryingCaller;
    }

    public Application evaluate(Application application) {
        return retryingCaller.execute("Decision Rules Service", "INT-DRS-01", RETRY_POLICY, () ->
                restClient.post()
                        .uri("/v1/applications/evaluate")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .body(application)
                        .retrieve()
                        .body(Application.class));
    }
}
