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

class ClearbankMockTest {

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
    void bc01FullMatchIsDefaultOutcome() throws Exception {
        assertResultCode("ACCT-GOOD", "BC01");
    }

    @Test
    void bc02PartialNameMatch() throws Exception {
        assertResultCode("BC02-ACCT", "BC02");
    }

    @Test
    void bc03AccountClosed() throws Exception {
        assertResultCode("BC03-ACCT", "BC03");
    }

    @Test
    void bc04AccountNotFoundIsReturnedAsHttp200NotHttp404() throws Exception {
        HttpResponse<String> response = call(requestBody("BC04-ACCT"), null);

        assertEquals(200, response.statusCode());
        assertEquals("BC04", MAPPER.readTree(response.body()).get("resultCode").asText());
    }

    @Test
    void bc05OwnerMismatch() throws Exception {
        assertResultCode("BC05-ACCT", "BC05");
    }

    @Test
    void bc06PendingAtBureau() throws Exception {
        assertResultCode("BC06-ACCT", "BC06");
    }

    @Test
    void retryThenSuccessFailsTwiceThenSucceedsOnThirdAttempt() throws Exception {
        HttpResponse<String> attempt1 = call(requestBody("ACCT-GOOD"), "retry-then-success");
        HttpResponse<String> attempt2 = call(requestBody("ACCT-GOOD"), "retry-then-success");
        HttpResponse<String> attempt3 = call(requestBody("ACCT-GOOD"), "retry-then-success");

        assertEquals(503, attempt1.statusCode());
        assertEquals(503, attempt2.statusCode());
        assertEquals(200, attempt3.statusCode());
        assertEquals("BC01", MAPPER.readTree(attempt3.body()).get("resultCode").asText());
    }

    @Test
    void retryExhaustedAlwaysFails() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(503, call(requestBody("ACCT-GOOD"), "retry-exhausted").statusCode());
        }
    }

    @Test
    void rateLimitedReturns429WithRetryAfter() throws Exception {
        HttpResponse<String> response = call(requestBody("ACCT-GOOD"), "rate-limited");

        assertEquals(429, response.statusCode());
        assertEquals("2", response.headers().firstValue("Retry-After").orElse(null));
    }

    @Test
    void timeoutScenarioExceedsClientTimeout() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v1/account-checks"))
                .timeout(Duration.ofSeconds(1))
                .header("Content-Type", "application/json")
                .header("X-Test-Scenario", "timeout")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody("ACCT-GOOD")))
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

    private void assertResultCode(String accountNumber, String expectedCode) throws Exception {
        HttpResponse<String> response = call(requestBody(accountNumber), null);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertEquals(expectedCode, body.get("resultCode").asText());
    }

    private HttpResponse<String> call(String body, String testScenario) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/v1/account-checks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (testScenario != null) {
            builder.header("X-Test-Scenario", testScenario);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String requestBody(String accountNumber) {
        return """
                {
                  "accountHolderName": "Jane Doe",
                  "bankName": "Example Bank",
                  "accountNumber": "%s",
                  "branchCode": "123456",
                  "reference": "APP-1"
                }
                """.formatted(accountNumber);
    }
}
