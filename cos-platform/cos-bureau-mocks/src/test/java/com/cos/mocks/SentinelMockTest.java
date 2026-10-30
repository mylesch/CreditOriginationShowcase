package com.cos.mocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentinelMockTest {

    private static WireMockServer server;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void startServer() {
        server = BureauMockServer.create(0);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @AfterEach
    void resetScenarios() {
        server.resetScenarios();
    }

    @Test
    void cleanScreeningReturnsNoMatches() throws Exception {
        HttpResponse<String> response = call(requestBody("Jane Doe"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertFalse(body.get("overallMatch").asBoolean());
        assertEquals(0, body.get("matches").size());
    }

    @Test
    void pepMatchReturnsOverallMatchTrueWithMatchDetail() throws Exception {
        HttpResponse<String> response = call(requestBody("PEP Match Subject"), null);

        JsonNode body = MAPPER.readTree(response.body());
        assertTrue(body.get("overallMatch").asBoolean());
        assertEquals("PEP", body.get("matches").get(0).get("list").asText());
    }

    @Test
    void retryThenSuccessFailsTwiceThenSucceedsOnThirdAttempt() throws Exception {
        HttpResponse<String> attempt1 = call(requestBody("Jane Doe"), "retry-then-success");
        HttpResponse<String> attempt2 = call(requestBody("Jane Doe"), "retry-then-success");
        HttpResponse<String> attempt3 = call(requestBody("Jane Doe"), "retry-then-success");

        assertEquals(503, attempt1.statusCode());
        assertEquals(503, attempt2.statusCode());
        assertEquals(200, attempt3.statusCode());
    }

    @Test
    void retryExhaustedAlwaysFails() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(503, call(requestBody("Jane Doe"), "retry-exhausted").statusCode());
        }
    }

    @Test
    void rateLimitedReturns429WithRetryAfter() throws Exception {
        HttpResponse<String> response = call(requestBody("Jane Doe"), "rate-limited");

        assertEquals(429, response.statusCode());
        assertEquals("2", response.headers().firstValue("Retry-After").orElse(null));
    }

    @Test
    void timeoutScenarioExceedsClientTimeout() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v1/screen"))
                .timeout(Duration.ofSeconds(1))
                .header("Content-Type", "application/json")
                .header("X-Test-Scenario", "timeout")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody("Jane Doe")))
                .build();

        assertTrue(assertThrowsTimeout(request));
    }

    private boolean assertThrowsTimeout(HttpRequest request) {
        try {
            HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private HttpResponse<String> call(String body, String testScenario) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v1/screen"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (testScenario != null) {
            builder.header("X-Test-Scenario", testScenario);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String requestBody(String fullName) {
        return """
                {
                  "subject": {
                    "fullName": "%s",
                    "dateOfBirth": "1990-01-01",
                    "nationality": "GB",
                    "subjectType": "INDIVIDUAL"
                  },
                  "lists": ["PEP", "SANCTIONS", "WATCHLIST"],
                  "reference": "APP-1"
                }
                """.formatted(fullName);
    }
}
