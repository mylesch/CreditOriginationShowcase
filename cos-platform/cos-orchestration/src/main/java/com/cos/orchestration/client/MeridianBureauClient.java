package com.cos.orchestration.client;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.enums.IdentificationType;
import com.cos.domain.enums.LiabilityCategory;
import com.cos.domain.enums.LiabilitySource;
import com.cos.domain.enums.PartyType;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Liability;
import com.cos.domain.model.Party;
import com.cos.orchestration.client.dto.MeridianDtos.LiabilityDto;
import com.cos.orchestration.client.dto.MeridianDtos.Request;
import com.cos.orchestration.client.dto.MeridianDtos.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calls Meridian Bureau - Credit Report. See the Integration Workbook's "Meridian Bureau API" tab
 * and the Process Workbook's "Call Meridian Bureau Service" tab. Unlike the other bureaus,
 * Meridian only allows 1 retry (not 2), and only on 503/504 - not the full 5xx range.
 */
@Component
public class MeridianBureauClient {

    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(List.of(Duration.ofSeconds(2)), Set.of(503, 504));

    private final RestClient restClient;
    private final RetryingCaller retryingCaller;

    public MeridianBureauClient(@Value("${cos.orchestration.bureaus.meridian.base-url}") String baseUrl,
                                  RetryingCaller retryingCaller) {
        this.restClient = RestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(12));
        this.retryingCaller = retryingCaller;
    }

    public BureauCallOutcome fetchCreditReport(Application application, Party party) {
        boolean consumer = party.getPartyType() == PartyType.Consumer;
        try {
            Response response = retryingCaller.execute("Meridian Bureau", "INT-MB-01", RETRY_POLICY, () ->
                    restClient.post()
                            .uri("/v2/credit-reports")
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .body(buildRequest(application, party, consumer))
                            .retrieve()
                            .body(Response.class));
            return mapResponse(party, consumer, response);
        } catch (IntegrationCallException e) {
            return BureauCallOutcome.failure(party.getPartyId(), ApplicationErrors.fromIntegrationFailure(e));
        }
    }

    private Request buildRequest(Application application, Party party, boolean consumer) {
        Request.Subject subject;
        if (consumer) {
            String nationalId = party.getIdentifications() == null ? null : party.getIdentifications().stream()
                    .filter(i -> i.getIdentificationType() == IdentificationType.NationalID)
                    .findFirst().map(i -> i.getReference()).orElse(null);
            subject = new Request.Subject(
                    party.getPerson() == null ? null : party.getPerson().getFirstName(),
                    party.getPerson() == null ? null : party.getPerson().getLastName(),
                    party.getPerson() == null ? null : party.getPerson().getDateOfBirth(),
                    nationalId, null);
        } else {
            subject = new Request.Subject(null, null, null, null,
                    party.getCompany() == null ? null : party.getCompany().getRegistrationNumber());
        }
        return new Request(consumer ? "CONSUMER" : "COMMERCIAL", subject, "FULL", application.getApplicationId());
    }

    private BureauCallOutcome mapResponse(Party party, boolean consumer, Response response) {
        boolean isSuccessful = "COMPLETE".equals(response.status()) && response.error() == null;
        BureauResponse.BureauResponseBuilder builder = BureauResponse.builder()
                .bureauName(BureauName.MERIDIAN_BUREAU)
                .checkType(consumer ? BureauCheckType.ConsumerCredit : BureauCheckType.CommercialCredit)
                .correlationId(response.reference())
                .isSuccessful(isSuccessful)
                .dateOfProvision(response.reportDate());
        if (Boolean.TRUE.equals(response.recordFound()) && response.score() != null) {
            builder.numericBureauScore(response.score().value());
        }
        if (response.error() != null) {
            builder.errorCode(response.error().code()).errorMessage(response.error().message());
        }

        List<Liability> liabilities = toLiabilities(response.liabilities());
        BigDecimal yearsTradingOverride = consumer || response.tradingHistoryMonths() == null ? null
                : BigDecimal.valueOf(response.tradingHistoryMonths()).divide(BigDecimal.valueOf(12));

        return BureauCallOutcome.success(party.getPartyId(), builder.build(), liabilities, yearsTradingOverride);
    }

    private List<Liability> toLiabilities(List<LiabilityDto> liabilities) {
        if (liabilities == null) {
            return List.of();
        }
        return liabilities.stream().map(l -> Liability.builder()
                .category(toCategory(l.category()))
                .balance(l.balance())
                .limit(l.limit())
                .monthlyRepayment(l.monthlyRepayment())
                .arrears(l.arrearsAmount())
                .isJointAccount(l.isJointAccount())
                .source(LiabilitySource.Bureau)
                .build()).toList();
    }

    private LiabilityCategory toCategory(String category) {
        if (category == null) {
            return LiabilityCategory.Other;
        }
        try {
            return LiabilityCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            return LiabilityCategory.Other;
        }
    }
}
