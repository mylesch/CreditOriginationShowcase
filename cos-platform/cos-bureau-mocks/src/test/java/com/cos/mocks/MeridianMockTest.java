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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianMockTest {

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
    void consumerWithCreditFileReturnsScoreAndLiabilities() throws Exception {
        HttpResponse<String> response = call(consumerBody("REF-123"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertTrue(body.get("recordFound").asBoolean());
        assertEquals(712, body.get("score").get("value").asInt());
        assertEquals(1, body.get("liabilities").size());
    }

    @Test
    void consumerWithNoTraceReturnsRecordFoundFalseAsHttp200() throws Exception {
        HttpResponse<String> response = call(consumerBody("NOTRACE-ID"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertFalse(body.get("recordFound").asBoolean());
        assertFalse(body.has("score"));
    }

    @Test
    void commercialWithNoTraceReturnsRecordFoundFalseAsHttp200() throws Exception {
        HttpResponse<String> response = call(commercialBody("NOTRACE-REG"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertFalse(body.get("recordFound").asBoolean());
    }

    @Test
    void retryThenSuccessFailsOnceThenSucceedsOnSecondAttempt() throws Exception {
        HttpResponse<String> attempt1 = call(consumerBody("REF-123"), "retry-then-success");
        HttpResponse<String> attempt2 = call(consumerBody("REF-123"), "retry-then-success");

        assertEquals(503, attempt1.statusCode());
        assertEquals(200, attempt2.statusCode());
        assertTrue(MAPPER.readTree(attempt2.body()).get("recordFound").asBoolean());
    }

    @Test
    void retryExhaustedAlwaysFails() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertEquals(503, call(consumerBody("REF-123"), "retry-exhausted").statusCode());
        }
    }

    @Test
    void nonRetryableErrorFailsClosedWithNoRetry() throws Exception {
        HttpResponse<String> response = call(consumerBody("REF-123"), "non-retryable-error");

        assertEquals(500, response.statusCode());
    }

    private HttpResponse<String> call(String body, String testScenario) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v2/credit-reports"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (testScenario != null) {
            builder.header("X-Test-Scenario", testScenario);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String consumerBody(String nationalIdNumber) {
        return """
                {
                  "subjectType": "CONSUMER",
                  "subject": {
                    "firstName": "Jane",
                    "lastName": "Doe",
                    "dateOfBirth": "1990-01-01",
                    "nationalIdNumber": "%s",
                    "companyRegistrationNumber": null
                  },
                  "reportType": "FULL",
                  "reference": "REF-123"
                }
                """.formatted(nationalIdNumber);
    }

    private String commercialBody(String companyRegistrationNumber) {
        return """
                {
                  "subjectType": "COMMERCIAL",
                  "subject": {
                    "companyRegistrationNumber": "%s"
                  },
                  "reportType": "FULL",
                  "reference": "REF-456"
                }
                """.formatted(companyRegistrationNumber);
    }
}
