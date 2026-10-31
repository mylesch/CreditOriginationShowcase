package com.cos.orchestration;

import com.cos.decision.CosDecisionServiceApplication;
import com.cos.domain.enums.AddressType;
import com.cos.domain.enums.ApplicationRouting;
import com.cos.domain.enums.ApplicationStatus;
import com.cos.domain.enums.BankAccountType;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.Channel;
import com.cos.domain.enums.IdentificationType;
import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.RequestType;
import com.cos.domain.model.Address;
import com.cos.domain.model.Application;
import com.cos.domain.model.BankAccount;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Party;
import com.cos.domain.model.Person;
import com.cos.domain.model.ProductRequest;
import com.cos.mocks.BureauMockServer;
import com.cos.orchestration.client.ClearbankVerificationClient;
import com.cos.orchestration.client.DrsClient;
import com.cos.orchestration.client.MeridianBureauClient;
import com.cos.orchestration.client.NorthgateKybClient;
import com.cos.orchestration.client.NorthgateKycClient;
import com.cos.orchestration.client.RetryingCaller;
import com.cos.orchestration.client.SentinelComplianceClient;
import com.cos.orchestration.process.CreditOriginationOrchestrator;
import com.cos.orchestration.process.GatherExternalDataService;
import com.cos.orchestration.process.ValidateRequestPayloadService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves DRS (real Drools rules), the bureau mocks (real WireMock) and this module's
 * orchestrator work together end to end. No Spring context of its own - starting two full
 * Spring Boot apps side by side under one @SpringBootTest would fight over context lifecycle
 * ordering, so the orchestrator's components are wired up directly as plain objects once both
 * real servers' ports are known.
 */
class OrchestrationEndToEndTest {

    private static WireMockServer bureauMockServer;
    private static ConfigurableApplicationContext drsContext;
    private static ExecutorService executor;
    private static CreditOriginationOrchestrator orchestrator;

    @BeforeAll
    static void startServers() {
        bureauMockServer = BureauMockServer.create(0);
        bureauMockServer.start();
        String bureauBaseUrl = "http://localhost:" + bureauMockServer.port();

        drsContext = new SpringApplicationBuilder(CosDecisionServiceApplication.class)
                .properties("server.port=0", "cos.chaos.enabled=false")
                .run();
        String drsBaseUrl = "http://localhost:" + drsContext.getEnvironment().getProperty("local.server.port");

        executor = Executors.newFixedThreadPool(5);
        RetryingCaller retryingCaller = new RetryingCaller();
        GatherExternalDataService gatherExternalDataService = new GatherExternalDataService(
                new NorthgateKycClient(bureauBaseUrl, retryingCaller),
                new NorthgateKybClient(bureauBaseUrl, retryingCaller),
                new MeridianBureauClient(bureauBaseUrl, retryingCaller),
                new SentinelComplianceClient(bureauBaseUrl, retryingCaller),
                new ClearbankVerificationClient(bureauBaseUrl, retryingCaller),
                executor);
        orchestrator = new CreditOriginationOrchestrator(new ValidateRequestPayloadService(),
                new DrsClient(drsBaseUrl, retryingCaller), gatherExternalDataService, 3);
    }

    @AfterAll
    static void stopServers() {
        bureauMockServer.stop();
        drsContext.close();
        executor.shutdown();
    }

    @Test
    void freshConsumerApplicationResolvesThroughTheExternalDataRequiredCycle() {
        Application application = freshConsumerApplication();

        Application result = orchestrator.originate(application);

        assertNotEquals(ApplicationRouting.EXTERNAL_DATA_REQUIRED, result.getDecisions().getApplicationRouting());
        Party party = result.getParties().get(0);
        List<BureauCheckType> checkTypes = party.getBureauResponses().stream().map(r -> r.getCheckType()).toList();
        assertTrue(checkTypes.contains(BureauCheckType.KYC));
        assertTrue(checkTypes.contains(BureauCheckType.ConsumerCredit));
        assertTrue(checkTypes.contains(BureauCheckType.Compliance));
        assertTrue(checkTypes.contains(BureauCheckType.BankCheck));
        assertEquals(2, result.getDrsCycleCount());
    }

    @Test
    void invalidApplicationIsRejectedWithoutCallingDrsOrBureaus() {
        Application application = Application.builder().applicationId("APP-INVALID").build();

        Application result = orchestrator.originate(application);

        assertEquals(ApplicationStatus.REJECTED, result.getStatus());
        assertTrue(result.getErrors().stream().allMatch(e -> "VAL01".equals(e.getCode())));
    }

    private Application freshConsumerApplication() {
        Party applicant = Party.builder()
                .partyId("P1")
                .partyRole(PartyRole.Applicant)
                .partyType(PartyType.Consumer)
                .person(Person.builder().firstName("Jane").lastName("Doe")
                        .dateOfBirth(LocalDate.of(1990, 1, 1)).nationality("GB").build())
                .identifications(List.of(Identification.builder()
                        .identificationType(IdentificationType.NationalID).reference("8001015009087").build()))
                .addresses(List.of(Address.builder().addressType(AddressType.Residential)
                        .line1("1 Example Street").city("Sampleton").postalCode("AB1 2CD").country("GB").build()))
                .bankAccounts(List.of(BankAccount.builder().accountType(BankAccountType.Current)
                        .accountHolderName("Jane Doe").bankName("Example Bank")
                        .accountNumber("ACCT-GOOD").branchCode("123456").isPrimary(true).build()))
                .build();

        return Application.builder()
                .applicationId("APP-E2E-1")
                .applicationTimestamp(LocalDateTime.now())
                .channel(Channel.Online)
                .countryCode("GB")
                .requestType(RequestType.FULL_ORIGINATION)
                .customerType(PartyType.Consumer)
                .hasCreditApproval(Boolean.FALSE)
                .parties(List.of(applicant))
                .productRequests(List.of(ProductRequest.builder()
                        .productRequestId("PR1").productCode("PERSONAL_LOAN")
                        .requestedAmount(BigDecimal.valueOf(5000)).termMonths(24).build()))
                .build();
    }
}
