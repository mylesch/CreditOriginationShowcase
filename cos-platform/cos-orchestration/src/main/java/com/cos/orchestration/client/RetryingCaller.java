package com.cos.orchestration.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Shared retry/backoff mechanism for every outbound call (DRS and the four bureau clients).
 * Per the Integration Workbook's Connection Principles: retries apply only to timeouts and the
 * documented 5xx statuses, never to 4xx; backoff uses the per-service delays plus a little
 * jitter.
 */
@Component
public class RetryingCaller {

    public <T> T execute(String serviceName, String errorCode, RetryPolicy policy, Supplier<T> attempt) {
        int retryAttempt = 0;
        while (true) {
            try {
                return attempt.get();
            } catch (HttpClientErrorException e) {
                throw new IntegrationCallException(serviceName, errorCode,
                        serviceName + " returned " + e.getStatusCode() + " (not retried)", retryAttempt, e);
            } catch (HttpServerErrorException e) {
                if (retryAttempt >= policy.maxRetries() || !policy.isRetryableStatus(e.getStatusCode().value())) {
                    throw new IntegrationCallException(serviceName, errorCode,
                            serviceName + " returned " + e.getStatusCode() + " after " + retryAttempt + " retries",
                            retryAttempt, e);
                }
            } catch (ResourceAccessException e) {
                if (retryAttempt >= policy.maxRetries()) {
                    throw new IntegrationCallException(serviceName, errorCode,
                            serviceName + " timed out after " + retryAttempt + " retries", retryAttempt, e);
                }
            }
            sleepWithJitter(policy.delayBeforeRetry(retryAttempt));
            retryAttempt++;
        }
    }

    private void sleepWithJitter(Duration delay) {
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, 250);
        try {
            Thread.sleep(delay.toMillis() + jitterMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff", e);
        }
    }
}
