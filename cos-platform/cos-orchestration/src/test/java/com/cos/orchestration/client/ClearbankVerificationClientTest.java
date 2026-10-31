package com.cos.orchestration.client;

import com.cos.domain.enums.BankCheckResultCode;
import com.cos.domain.model.Application;
import com.cos.domain.model.BankAccount;
import com.cos.domain.model.Party;
import com.cos.mocks.BureauMockServer;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearbankVerificationClientTest {

    private static WireMockServer mockServer;
    private static ClearbankVerificationClient client;

    @BeforeAll
    static void startServer() {
        mockServer = BureauMockServer.create(0);
        mockServer.start();
        client = new ClearbankVerificationClient("http://localhost:" + mockServer.port(), new RetryingCaller());
    }

    @AfterAll
    static void stopServer() {
        mockServer.stop();
    }

    @Test
    void fullMatchMapsToBC01() {
        Application application = Application.builder().applicationId("APP-1").build();
        Party party = partyWithAccount("ACCT-GOOD");

        BureauCallOutcome outcome = client.checkAccount(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(BankCheckResultCode.BC01, outcome.bureauResponse().getBankCheckResult());
    }

    @Test
    void accountNotFoundIsStillAnHttp200WithBC04() {
        Application application = Application.builder().applicationId("APP-1").build();
        Party party = partyWithAccount("BC04-ACCT");

        BureauCallOutcome outcome = client.checkAccount(application, party);

        assertTrue(outcome.isSuccess());
        assertEquals(BankCheckResultCode.BC04, outcome.bureauResponse().getBankCheckResult());
    }

    private Party partyWithAccount(String accountNumber) {
        return Party.builder().partyId("P1")
                .bankAccounts(List.of(BankAccount.builder()
                        .accountHolderName("Jane Doe").bankName("Example Bank")
                        .accountNumber(accountNumber).branchCode("123456").isPrimary(true).build()))
                .build();
    }
}
