package com.cos.orchestration.process;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.enums.ErrorSource;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationDecisions;
import com.cos.domain.model.ApplicationError;
import com.cos.domain.model.BureauDataRequest;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.ExternalDataRequired;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Liability;
import com.cos.domain.model.Party;
import com.cos.domain.model.Person;
import com.cos.orchestration.client.BureauCallOutcome;
import com.cos.orchestration.client.ClearbankVerificationClient;
import com.cos.orchestration.client.MeridianBureauClient;
import com.cos.orchestration.client.NorthgateKybClient;
import com.cos.orchestration.client.NorthgateKycClient;
import com.cos.orchestration.client.SentinelComplianceClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatherExternalDataServiceTest {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(5);

    private NorthgateKycClient northgateKycClient;
    private NorthgateKybClient northgateKybClient;
    private MeridianBureauClient meridianBureauClient;
    private SentinelComplianceClient sentinelComplianceClient;
    private ClearbankVerificationClient clearbankVerificationClient;
    private GatherExternalDataService service;

    @AfterAll
    static void shutdown() {
        EXECUTOR.shutdown();
    }

    @BeforeEach
    void setUp() {
        northgateKycClient = mock(NorthgateKycClient.class);
        northgateKybClient = mock(NorthgateKybClient.class);
        meridianBureauClient = mock(MeridianBureauClient.class);
        sentinelComplianceClient = mock(SentinelComplianceClient.class);
        clearbankVerificationClient = mock(ClearbankVerificationClient.class);
        service = new GatherExternalDataService(northgateKycClient, northgateKybClient, meridianBureauClient,
                sentinelComplianceClient, clearbankVerificationClient, EXECUTOR);
    }

    @Test
    void routesEachCheckTypeToItsClientAndMergesTheBureauResponse() {
        Party party = Party.builder().partyId("P1").person(Person.builder().build()).build();
        Application application = applicationWithRequests(party,
                request("P1", BureauCheckType.KYC), request("P1", BureauCheckType.Compliance));

        when(northgateKycClient.verify(any(), any())).thenReturn(
                BureauCallOutcome.success("P1", bureauResponse(BureauCheckType.KYC)));
        when(sentinelComplianceClient.screen(any(), any())).thenReturn(
                BureauCallOutcome.success("P1", bureauResponse(BureauCheckType.Compliance)));

        service.gather(application);

        assertEquals(2, party.getBureauResponses().size());
        assertTrue(party.getBureauResponses().stream().anyMatch(r -> r.getCheckType() == BureauCheckType.KYC));
        assertTrue(party.getBureauResponses().stream().anyMatch(r -> r.getCheckType() == BureauCheckType.Compliance));
    }

    @Test
    void failedCallAppendsApplicationErrorWithoutAddingBureauResponse() {
        Party party = Party.builder().partyId("P1").build();
        Application application = applicationWithRequests(party, request("P1", BureauCheckType.BankCheck));

        when(clearbankVerificationClient.checkAccount(any(), any())).thenReturn(
                BureauCallOutcome.failure("P1", ApplicationError.builder().code("INT-CB-01")
                        .source(ErrorSource.Integration).build()));

        service.gather(application);

        assertTrue(party.getBureauResponses() == null || party.getBureauResponses().isEmpty());
        assertEquals(1, application.getErrors().size());
        assertEquals("INT-CB-01", application.getErrors().get(0).getCode());
    }

    @Test
    void partialFailureStillMergesTheSuccessfulCalls() {
        Party party = Party.builder().partyId("P1").build();
        Application application = applicationWithRequests(party,
                request("P1", BureauCheckType.KYC), request("P1", BureauCheckType.Compliance));

        when(northgateKycClient.verify(any(), any())).thenReturn(
                BureauCallOutcome.success("P1", bureauResponse(BureauCheckType.KYC)));
        when(sentinelComplianceClient.screen(any(), any())).thenReturn(
                BureauCallOutcome.failure("P1", ApplicationError.builder().code("INT-SC-01")
                        .source(ErrorSource.Integration).build()));

        service.gather(application);

        assertEquals(1, party.getBureauResponses().size());
        assertEquals(1, application.getErrors().size());
    }

    @Test
    void liabilitiesAndIdentificationOverridesAreMergedOntoTheParty() {
        Party party = Party.builder().partyId("P1")
                .person(Person.builder().build())
                .identifications(List.of(Identification.builder().build()))
                .build();
        Application application = applicationWithRequests(party, request("P1", BureauCheckType.KYC));

        Liability liability = Liability.builder().balance(BigDecimal.TEN).build();
        when(northgateKycClient.verify(any(), any())).thenReturn(new BureauCallOutcome("P1",
                bureauResponse(BureauCheckType.KYC), null, List.of(liability), null, Boolean.TRUE));

        service.gather(application);

        assertEquals(1, party.getPerson().getLiabilities().size());
        assertEquals(Boolean.TRUE, party.getIdentifications().get(0).getIsVerified());
    }

    private Application applicationWithRequests(Party party, BureauDataRequest... requests) {
        return Application.builder()
                .applicationId("APP-1")
                .parties(List.of(party))
                .decisions(ApplicationDecisions.builder()
                        .externalDataRequired(ExternalDataRequired.builder()
                                .bureauDataRequests(List.of(requests)).build())
                        .build())
                .build();
    }

    private BureauDataRequest request(String partyId, BureauCheckType checkType) {
        return BureauDataRequest.builder().partyId(partyId).checkType(checkType).build();
    }

    private BureauResponse bureauResponse(BureauCheckType checkType) {
        return BureauResponse.builder().bureauName(BureauName.NORTHGATE_IDENTITY_SERVICES).checkType(checkType).build();
    }
}
