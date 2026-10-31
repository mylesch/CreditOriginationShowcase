package com.cos.orchestration.client;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Party;
import com.cos.orchestration.client.dto.SentinelDtos.Request;
import com.cos.orchestration.client.dto.SentinelDtos.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calls Sentinel Compliance Screening - PEPs / Sanctions / Watchlist. See the Integration
 * Workbook's "Sentinel Compliance API" tab and the Process Workbook's "Call Sentinel Compliance
 * Service" tab. A match never auto-declines here - Customer PEPs Status (Decision Workbook)
 * always routes a match to manual review.
 */
@Component
public class SentinelComplianceClient {

    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), Set.of(502, 503, 504));
    private static final List<String> SCREENING_LISTS = List.of("PEP", "SANCTIONS", "WATCHLIST");

    private final RestClient restClient;
    private final RetryingCaller retryingCaller;

    public SentinelComplianceClient(@Value("${cos.orchestration.bureaus.sentinel.base-url}") String baseUrl,
                                      RetryingCaller retryingCaller) {
        this.restClient = RestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(6));
        this.retryingCaller = retryingCaller;
    }

    public BureauCallOutcome screen(Application application, Party party) {
        try {
            Response response = retryingCaller.execute("Sentinel Compliance Screening", "INT-SC-01", RETRY_POLICY,
                    () -> restClient.post()
                            .uri("/v1/screen")
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .header("X-Screening-Purpose", "CREDIT_ORIGINATION")
                            .body(buildRequest(application, party))
                            .retrieve()
                            .body(Response.class));
            return mapResponse(party.getPartyId(), response);
        } catch (IntegrationCallException e) {
            return BureauCallOutcome.failure(party.getPartyId(), ApplicationErrors.fromIntegrationFailure(e));
        }
    }

    private Request buildRequest(Application application, Party party) {
        boolean individual = party.getPartyType() == PartyType.Consumer || party.getPerson() != null;
        Request.Subject subject = individual
                ? new Request.Subject(
                        fullName(party), party.getPerson() == null ? null : party.getPerson().getDateOfBirth(),
                        party.getPerson() == null ? null : party.getPerson().getNationality(), "INDIVIDUAL")
                : new Request.Subject(
                        party.getCompany() == null ? null : party.getCompany().getLegalName(), null, null, "ENTITY");
        return new Request(subject, SCREENING_LISTS, application.getApplicationId());
    }

    private String fullName(Party party) {
        if (party.getPerson() == null) {
            return null;
        }
        String first = party.getPerson().getFirstName() == null ? "" : party.getPerson().getFirstName();
        String last = party.getPerson().getLastName() == null ? "" : party.getPerson().getLastName();
        return (first + " " + last).trim();
    }

    private BureauCallOutcome mapResponse(String partyId, Response response) {
        boolean isSuccessful = "COMPLETE".equals(response.status()) && response.error() == null;
        BureauResponse.BureauResponseBuilder builder = BureauResponse.builder()
                .bureauName(BureauName.SENTINEL_COMPLIANCE_SCREENING)
                .checkType(BureauCheckType.Compliance)
                .correlationId(response.reference())
                .isSuccessful(isSuccessful)
                .isPEPsMatch(response.overallMatch())
                .dateOfProvision(response.screenedOn() == null ? null
                        : response.screenedOn().atZone(ZoneOffset.UTC).toLocalDate());
        if (response.error() != null) {
            builder.errorCode(response.error().code()).errorMessage(response.error().message());
        }
        return BureauCallOutcome.success(partyId, builder.build());
    }
}
