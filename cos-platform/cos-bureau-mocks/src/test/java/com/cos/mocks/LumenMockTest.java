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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenMockTest {

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
    void successfulAnalysisClassifiesAndExtractsFields() throws Exception {
        HttpResponse<String> response = call(requestBody("doc-123"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("Payslip", body.get("classification").get("documentType").asText());
        assertEquals(0, body.get("qualityFlags").size());
    }

    @Test
    void lowQualityDocumentReturnsQualityFlagAndLowConfidence() throws Exception {
        HttpResponse<String> response = call(requestBody("LOWCONF-DOC"), null);

        JsonNode body = MAPPER.readTree(response.body());
        assertTrue(body.get("classification").get("confidence").asDouble() < 0.5);
        assertEquals("LOW_RESOLUTION", body.get("qualityFlags").get(0).asText());
    }

    @Test
    void unsupportedFileTypeReturns415AndIsNotRetried() throws Exception {
        HttpResponse<String> response = call(requestBody("doc-123"), "unsupported-file-type");

        assertEquals(415, response.statusCode());
    }

    @Test
    void retryThenSuccessFailsOnceThenSucceedsOnSecondAttempt() throws Exception {
        HttpResponse<String> attempt1 = call(requestBody("doc-123"), "retry-then-success");
        HttpResponse<String> attempt2 = call(requestBody("doc-123"), "retry-then-success");

        assertEquals(503, attempt1.statusCode());
        assertEquals(200, attempt2.statusCode());
    }

    @Test
    void retryExhaustedAlwaysFails() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertEquals(503, call(requestBody("doc-123"), "retry-exhausted").statusCode());
        }
    }

    @Test
    void rateLimitedReturns429WithRetryAfter() throws Exception {
        HttpResponse<String> response = call(requestBody("doc-123"), "rate-limited");

        assertEquals(429, response.statusCode());
        assertEquals("2", response.headers().firstValue("Retry-After").orElse(null));
    }

    @Test
    void timeoutScenarioExceedsClientTimeout() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v1/documents/analyze"))
                .timeout(Duration.ofSeconds(1))
                .header("Content-Type", "application/json")
                .header("X-Test-Scenario", "timeout")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody("doc-123")))
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
                .uri(URI.create(server.baseUrl() + "/v1/documents/analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (testScenario != null) {
            builder.header("X-Test-Scenario", testScenario);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String requestBody(String documentId) {
        return """
                {
                  "documentId": "%s",
                  "documentUrl": "https://docs.example.com/%s",
                  "expectedDocumentTypes": ["BankStatement", "Payslip", "ProofOfIdentity"],
                  "reference": "APP-1"
                }
                """.formatted(documentId, documentId);
    }
}
