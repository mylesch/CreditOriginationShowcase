package com.cos.orchestration.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class RetryingCallerTest {

    // Tiny durations so the test suite doesn't pay the real 1s/2s backoff a bureau's policy specifies.
    private static final RetryPolicy TWO_RETRIES =
            new RetryPolicy(List.of(Duration.ofMillis(5), Duration.ofMillis(5)), Set.of(502, 503, 504));

    private final RetryingCaller retryingCaller = new RetryingCaller();

    @Test
    void succeedsOnFirstAttemptWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();
        String result = retryingCaller.execute("svc", "E01", TWO_RETRIES, () -> {
            attempts.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void retriesOnRetryableStatusThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = retryingCaller.execute("svc", "E01", TWO_RETRIES, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw HttpServerErrorException.create(SERVICE_UNAVAILABLE, "", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void throwsAfterRetriesExhausted() {
        AtomicInteger attempts = new AtomicInteger();
        IntegrationCallException ex = assertThrows(IntegrationCallException.class, () ->
                retryingCaller.execute("svc", "E01", TWO_RETRIES, () -> {
                    attempts.incrementAndGet();
                    throw HttpServerErrorException.create(SERVICE_UNAVAILABLE, "", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
                }));

        assertEquals(3, attempts.get()); // initial attempt + 2 retries
        assertEquals(2, ex.getRetryCount());
        assertEquals("E01", ex.getCode());
    }

    @Test
    void neverRetries4xx() {
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IntegrationCallException.class, () ->
                retryingCaller.execute("svc", "E01", TWO_RETRIES, () -> {
                    attempts.incrementAndGet();
                    throw HttpClientErrorException.create(BAD_REQUEST, "", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
                }));

        assertEquals(1, attempts.get());
    }

    @Test
    void doesNotRetryNonRetryableServerStatus() {
        // 500 is not in the retryable set for this policy (mirrors Meridian, which only retries 503/504).
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IntegrationCallException.class, () ->
                retryingCaller.execute("svc", "E01", TWO_RETRIES, () -> {
                    attempts.incrementAndGet();
                    throw HttpServerErrorException.create(HttpStatusCode.valueOf(500), "", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
                }));

        assertEquals(1, attempts.get());
    }

    @Test
    void timeoutIsRetryable() {
        AtomicInteger attempts = new AtomicInteger();
        String result = retryingCaller.execute("svc", "E01", TWO_RETRIES, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new ResourceAccessException("timed out");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }
}
