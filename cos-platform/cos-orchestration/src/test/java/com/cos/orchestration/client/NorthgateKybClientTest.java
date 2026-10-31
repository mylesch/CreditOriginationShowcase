package com.cos.orchestration.client;

import com.cos.domain.enums.AddressType;
import com.cos.domain.enums.CompanyRegistrationStatus;
import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Address;
import com.cos.domain.model.Application;
import com.cos.domain.model.Company;
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

class NorthgateKybClientTest {

    private static WireMockServer mockServer;
    private static NorthgateKybClient client;

    @BeforeAll
    static void startServer() {
        mockServer = BureauMockServer.create(0);
        mockServer.start();
        client = new NorthgateKybClient("http://localhost:" + mockServer.port(), new RetryingCaller());
    }

    @AfterAll
    static void stopServer() {
        mockServer.stop();
    }

    @Test
    void successfulVerificationMapsActiveRegistrationAndDirectors() {
        Application application = applicationWithRepresentative("ACTIVE-REG");
        Party businessParty = businessParty("ACTIVE-REG");

        BureauCallOutcome outcome = client.verify(application, businessParty);

        assertTrue(outcome.isSuccess());
        assertEquals(Boolean.TRUE, outcome.bureauResponse().getIsIdentityMatch());
        assertEquals(CompanyRegistrationStatus.Active, outcome.bureauResponse().getCompanyRegistrationStatus());
        assertEquals(1, outcome.bureauResponse().getDirectors().size());
    }

    @Test
    void deregisteredCompanyIsMappedToDeregisteredStatus() {
        Application application = applicationWithRepresentative("DEREG-REG");
        Party businessParty = businessParty("DEREG-REG");

        BureauCallOutcome outcome = client.verify(application, businessParty);

        assertEquals(CompanyRegistrationStatus.Deregistered, outcome.bureauResponse().getCompanyRegistrationStatus());
    }

    private Application applicationWithRepresentative(String registrationNumber) {
        Party representative = Party.builder()
                .partyId("P2")
                .partyRole(PartyRole.Representative)
                .person(Person.builder().firstName("Rep").lastName("Resentative")
                        .dateOfBirth(LocalDate.of(1980, 1, 1)).build())
                .identifications(List.of(Identification.builder().reference("REP-001").build()))
                .build();
        return Application.builder().applicationId("APP-1")
                .parties(List.of(businessParty(registrationNumber), representative)).build();
    }

    private Party businessParty(String registrationNumber) {
        return Party.builder()
                .partyId("P1")
                .partyType(PartyType.Business)
                .partyRole(PartyRole.Applicant)
                .company(Company.builder().legalName("Example Trading Ltd")
                        .registrationNumber(registrationNumber).build())
                .addresses(List.of(Address.builder().addressType(AddressType.RegisteredOffice).country("GB").build()))
                .build();
    }
}
