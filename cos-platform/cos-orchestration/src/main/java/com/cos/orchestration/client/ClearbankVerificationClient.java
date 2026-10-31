package com.cos.orchestration.client;

import com.cos.domain.enums.BankCheckResultCode;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.model.Application;
import com.cos.domain.model.BankAccount;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Party;
import com.cos.orchestration.client.dto.ClearbankDtos.Request;
import com.cos.orchestration.client.dto.ClearbankDtos.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calls Clearbank Verification Service - Bank Account Check. See the Integration Workbook's
 * "Clearbank Verification API" tab and the Process Workbook's "Call Clearbank Verification
 * Service" tab. The result code (BC01-BC06) is passed through verbatim - the Bank Check Result
 * decision (Decision Workbook) interprets it, not this client.
 */
@Component
public class ClearbankVerificationClient {

    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), Set.of(502, 503, 504));

    private final RestClient restClient;
    private final RetryingCaller retryingCaller;

    public ClearbankVerificationClient(@Value("${cos.orchestration.bureaus.clearbank.base-url}") String baseUrl,
                                         RetryingCaller retryingCaller) {
        this.restClient = RestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(6));
        this.retryingCaller = retryingCaller;
    }

    public BureauCallOutcome checkAccount(Application application, Party party) {
        try {
            Response response = retryingCaller.execute("Clearbank Verification Service", "INT-CB-01", RETRY_POLICY,
                    () -> restClient.post()
                            .uri("/v1/account-checks")
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .body(buildRequest(application, party))
                            .retrieve()
                            .body(Response.class));
            return mapResponse(party.getPartyId(), response);
        } catch (IntegrationCallException e) {
            return BureauCallOutcome.failure(party.getPartyId(), ApplicationErrors.fromIntegrationFailure(e));
        }
    }

    private Request buildRequest(Application application, Party party) {
        BankAccount primary = primaryAccount(party);
        return new Request(
                primary == null ? null : primary.getAccountHolderName(),
                primary == null ? null : primary.getBankName(),
                primary == null ? null : primary.getAccountNumber(),
                primary == null ? null : primary.getBranchCode(),
                application.getApplicationId());
    }

    private BureauCallOutcome mapResponse(String partyId, Response response) {
        boolean isSuccessful = "COMPLETE".equals(response.status()) && response.error() == null;
        BureauResponse.BureauResponseBuilder builder = BureauResponse.builder()
                .bureauName(BureauName.CLEARBANK_VERIFICATION_SERVICE)
                .checkType(BureauCheckType.BankCheck)
                .correlationId(response.reference())
                .isSuccessful(isSuccessful)
                .bankCheckResult(toResultCode(response.resultCode()))
                .dateOfProvision(response.checkedOn() == null ? null
                        : response.checkedOn().atZone(ZoneOffset.UTC).toLocalDate());
        if (response.error() != null) {
            builder.errorCode(response.error().code()).errorMessage(response.error().message());
        }
        return BureauCallOutcome.success(partyId, builder.build());
    }

    private BankCheckResultCode toResultCode(String resultCode) {
        if (resultCode == null) {
            return null;
        }
        try {
            return BankCheckResultCode.valueOf(resultCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BankAccount primaryAccount(Party party) {
        if (party.getBankAccounts() == null) {
            return null;
        }
        return party.getBankAccounts().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsPrimary()))
                .findFirst().orElse(null);
    }
}
