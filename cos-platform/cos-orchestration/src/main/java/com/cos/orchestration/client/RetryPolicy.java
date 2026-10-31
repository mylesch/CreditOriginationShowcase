package com.cos.orchestration.client;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * One entry per retry attempt in {@code backoffDelays} (e.g. two entries = two retries, three
 * total attempts). Matches each service's own documented policy in the Integration Workbook -
 * retry counts and backoff are not uniform across bureaus.
 */
public record RetryPolicy(List<Duration> backoffDelays, Set<Integer> retryableStatusCodes) {

    public int maxRetries() {
        return backoffDelays.size();
    }

    public boolean isRetryableStatus(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    public Duration delayBeforeRetry(int retryAttempt) {
        return backoffDelays.get(retryAttempt);
    }
}
