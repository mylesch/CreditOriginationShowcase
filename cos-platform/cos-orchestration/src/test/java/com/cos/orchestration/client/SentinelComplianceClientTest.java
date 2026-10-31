package com.cos.orchestration.client;

import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.Party;
import com.cos.domain.model.Person;
import com.cos.mocks.BureauMockServer;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentinelComplianceClientTest {

    private static WireMockServer mockServer;
    private static SentinelComplianceClient client;

    @BeforeAll
    static void startServer() {
        mockServer = BureauMockServer.create(0);
        mockServer.start();
        client = new SentinelComplianceClient("http://localhost:" + mockServer.port(), new RetryingCaller());
    }

    @AfterAll
    static void stopServer() {
        mockServer.stop();
    }

    @Test
    void cleanScreeningMapsNoMatch() {
        Application application = Application.builder().applicationId("APP-1").build();
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .person(Person.builder().firstName("Jane").lastName("Doe").build()).build();

        BureauCallOutcome outcome = client.screen(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(Boolean.FALSE, outcome.bureauResponse().getIsPEPsMatch());
    }

    @Test
    void pepMatchMapsIsPEPsMatchTrue() {
        Application application = Application.builder().applicationId("APP-1").build();
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .person(Person.builder().firstName("PEP Match").lastName("Subject").build()).build();

        BureauCallOutcome outcome = client.screen(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(Boolean.TRUE, outcome.bureauResponse().getIsPEPsMatch());
    }
}
