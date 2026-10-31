package com.cos.orchestration.client;

import com.cos.domain.enums.IdentificationType;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Party;
import com.cos.domain.model.Person;
import com.cos.mocks.BureauMockServer;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeridianBureauClientTest {

    private static WireMockServer mockServer;
    private static MeridianBureauClient client;

    @BeforeAll
    static void startServer() {
        mockServer = BureauMockServer.create(0);
        mockServer.start();
        client = new MeridianBureauClient("http://localhost:" + mockServer.port(), new RetryingCaller());
    }

    @AfterAll
    static void stopServer() {
        mockServer.stop();
    }

    @Test
    void consumerWithCreditFileMapsScoreAndLiabilities() {
        Application application = Application.builder().applicationId("APP-1").build();
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .person(Person.builder().firstName("Jane").lastName("Doe").dateOfBirth(LocalDate.of(1990, 1, 1))
                        .build())
                .identifications(List.of(Identification.builder()
                        .identificationType(IdentificationType.NationalID).reference("REF-123").build()))
                .build();

        BureauCallOutcome outcome = client.fetchCreditReport(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(712, outcome.bureauResponse().getNumericBureauScore().intValue());
        assertEquals(1, outcome.liabilitiesToMerge().size());
    }

    @Test
    void consumerWithNoTraceLeavesScoreUnset() {
        Application application = Application.builder().applicationId("APP-1").build();
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .person(Person.builder().firstName("Jane").lastName("Doe").build())
                .identifications(List.of(Identification.builder()
                        .identificationType(IdentificationType.NationalID).reference("NOTRACE-ID").build()))
                .build();

        BureauCallOutcome outcome = client.fetchCreditReport(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(null, outcome.bureauResponse().getNumericBureauScore());
    }
}
