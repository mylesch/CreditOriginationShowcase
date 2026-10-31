package com.cos.orchestration.client;

import com.cos.domain.enums.AddressType;
import com.cos.domain.enums.IdentificationType;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Address;
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

class NorthgateKycClientTest {

    private static WireMockServer mockServer;
    private static NorthgateKycClient client;

    @BeforeAll
    static void startServer() {
        mockServer = BureauMockServer.create(0);
        mockServer.start();
        client = new NorthgateKycClient("http://localhost:" + mockServer.port(), new RetryingCaller());
    }

    @AfterAll
    static void stopServer() {
        mockServer.stop();
    }

    @Test
    void successfulVerificationMapsMatchAndVerifiedIdentification() {
        Application application = application();
        Party party = consumerParty("8001015009087");

        BureauCallOutcome outcome = client.verify(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(Boolean.TRUE, outcome.bureauResponse().getIsIdentityMatch());
        assertEquals(Boolean.TRUE, outcome.identificationVerifiedOverride());
        assertEquals("KYC", outcome.bureauResponse().getCheckType().name());
    }

    @Test
    void noMatchMapsIsIdentityMatchFalseAndUnverifiedIdentification() {
        Application application = application();
        Party party = consumerParty("NOMATCH0001");

        BureauCallOutcome outcome = client.verify(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(Boolean.FALSE, outcome.bureauResponse().getIsIdentityMatch());
        assertEquals(Boolean.FALSE, outcome.identificationVerifiedOverride());
    }

    private Application application() {
        return Application.builder().applicationId("APP-1").build();
    }

    private Party consumerParty(String idNumber) {
        return Party.builder()
                .partyId("P1")
                .partyType(PartyType.Consumer)
                .person(Person.builder().firstName("Jane").lastName("Doe").dateOfBirth(LocalDate.of(1990, 1, 1))
                        .nationality("GB").build())
                .identifications(List.of(Identification.builder()
                        .identificationType(IdentificationType.NationalID).reference(idNumber).build()))
                .addresses(List.of(Address.builder().addressType(AddressType.Residential)
                        .line1("1 Example Street").city("Sampleton").postalCode("AB1 2CD").country("GB").build()))
                .build();
    }
}
