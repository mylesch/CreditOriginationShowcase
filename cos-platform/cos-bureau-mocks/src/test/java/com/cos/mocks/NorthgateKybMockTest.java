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

class NorthgateKybMockTest {

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
    void successfulVerificationReturnsActiveMatch() throws Exception {
        HttpResponse<String> response = call(requestBody("ACTIVE-REG"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("MATCH", body.get("result").get("matchOutcome").asText());
        assertEquals("ACTIVE", body.get("result").get("registrationStatus").asText());
    }

    @Test
    void unmatchedRegistrationNumberReturnsNoMatch() throws Exception {
        HttpResponse<String> response = call(requestBody("NOMATCH-REG"), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("NO_MATCH", body.get("result").get("matchOutcome").asText());
    }

    @Test
    void deregisteredCompanyIsFlaggedInRegistrationStatus() throws Exception {
        HttpResponse<String> response = call(requestBody("DEREG-REG"), null);

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("MATCH", body.get("result").get("matchOutcome").asText());
        assertEquals("DEREGISTERED", body.get("result").get("registrationStatus").asText());
    }

    @Test
    void inLiquidationCompanyIsFlaggedInRegistrationStatus() throws Exception {
        HttpResponse<String> response = call(requestBody("LIQ-REG"), null);

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("IN_LIQUIDATION", body.get("result").get("registrationStatus").asText());
    }

    @Test
    void unknownRegistrationStatusIsPassedThrough() throws Exception {
        HttpResponse<String> response = call(requestBody("UNKNOWN-REG"), null);

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("UNKNOWN", body.get("result").get("registrationStatus").asText());
    }

    @Test
    void retryThenSuccessFailsTwiceThenSucceedsOnThirdAttempt() throws Exception {
        HttpResponse<String> attempt1 = call(requestBody("ACTIVE-REG"), "retry-then-success");
        HttpResponse<String> attempt2 = call(requestBody("ACTIVE-REG"), "retry-then-success");
        HttpResponse<String> attempt3 = call(requestBody("ACTIVE-REG"), "retry-then-success");

        assertEquals(503, attempt1.statusCode());
        assertEquals(503, attempt2.statusCode());
        assertEquals(200, attempt3.statusCode());
        assertEquals("MATCH", MAPPER.readTree(attempt3.body()).get("result").get("matchOutcome").asText());
    }

    @Test
    void retryExhaustedAlwaysFails() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(503, call(requestBody("ACTIVE-REG"), "retry-exhausted").statusCode());
        }
    }

    @Test
    void rateLimitedReturns429WithRetryAfter() throws Exception {
        HttpResponse<String> response = call(requestBody("ACTIVE-REG"), "rate-limited");

        assertEquals(429, response.statusCode());
        assertEquals("2", response.headers().firstValue("Retry-After").orElse(null));
    }

    @Test
    void timeoutScenarioExceedsClientTimeout() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v1/companies/verify"))
                .timeout(Duration.ofSeconds(1))
                .header("Content-Type", "application/json")
                .header("X-Test-Scenario", "timeout")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody("ACTIVE-REG")))
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
                .uri(URI.create(server.baseUrl() + "/v1/companies/verify"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (testScenario != null) {
            builder.header("X-Test-Scenario", testScenario);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String requestBody(String registrationNumber) {
        return """
                {
                  "requestId": "req-123",
                  "subject": {
                    "legalName": "Example Trading Ltd",
                    "registrationNumber": "%s",
                    "registrationCountry": "GB",
                    "companyType": "PrivateLimited"
                  },
                  "representative": {
                    "firstName": "Rep",
                    "lastName": "Resentative",
                    "dateOfBirth": "1980-01-01",
                    "identificationNumber": "REP-001"
                  },
                  "consentReference": "APP-1"
                }
                """.formatted(registrationNumber);
    }
}
