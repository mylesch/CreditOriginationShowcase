package com.cos.orchestration.client;

import com.cos.domain.enums.AddressType;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.model.Address;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Party;
import com.cos.orchestration.client.dto.NorthgateKycDtos.Request;
import com.cos.orchestration.client.dto.NorthgateKycDtos.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calls Northgate Identity Services - Individual Verification (KYC). See the Integration
 * Workbook's "Northgate KYC API" tab and the Process Workbook's "Call Northgate KYC Service" tab.
 */
@Component
public class NorthgateKycClient {

    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), Set.of(502, 503, 504));

    private final RestClient restClient;
    private final RetryingCaller retryingCaller;

    public NorthgateKycClient(@Value("${cos.orchestration.bureaus.northgate.base-url}") String baseUrl,
                               RetryingCaller retryingCaller) {
        this.restClient = RestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(8));
        this.retryingCaller = retryingCaller;
    }

    public BureauCallOutcome verify(Application application, Party party) {
        try {
            Response response = retryingCaller.execute("Northgate Identity Services (KYC)", "INT-NG-KYC-01",
                    RETRY_POLICY, () -> restClient.post()
                            .uri("/v1/individuals/verify")
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .header("X-Client-Id", "cos-origination")
                            .body(buildRequest(application, party))
                            .retrieve()
                            .body(Response.class));
            return mapResponse(party.getPartyId(), response);
        } catch (IntegrationCallException e) {
            return BureauCallOutcome.failure(party.getPartyId(), ApplicationErrors.fromIntegrationFailure(e));
        }
    }

    private Request buildRequest(Application application, Party party) {
        Identification identification = firstIdentification(party);
        Address residential = residentialAddress(party);
        Request.Identification identificationDto = identification == null ? null : new Request.Identification(
                identification.getIdentificationType() == null ? null : identification.getIdentificationType().name(),
                identification.getReference(),
                identification.getPlaceIssued() != null ? identification.getPlaceIssued()
                        : residential != null ? residential.getCountry() : null,
                identification.getExpiresOn());
        Request.Address addressDto = residential == null ? null
                : new Request.Address(residential.getLine1(), residential.getCity(), residential.getPostalCode(),
                        residential.getCountry());
        Request.Subject subject = new Request.Subject(
                party.getPerson() == null ? null : party.getPerson().getFirstName(),
                party.getPerson() == null ? null : party.getPerson().getLastName(),
                party.getPerson() == null ? null : party.getPerson().getDateOfBirth(),
                party.getPerson() == null ? null : party.getPerson().getNationality(),
                identificationDto, addressDto);
        return new Request(UUID.randomUUID().toString(), subject, application.getApplicationId());
    }

    private BureauCallOutcome mapResponse(String partyId, Response response) {
        boolean isSuccessful = "COMPLETED".equals(response.status()) && response.error() == null;
        BureauResponse.BureauResponseBuilder builder = BureauResponse.builder()
                .bureauName(BureauName.NORTHGATE_IDENTITY_SERVICES)
                .checkType(BureauCheckType.KYC)
                .correlationId(response.requestId())
                .isSuccessful(isSuccessful)
                .dateOfProvision(response.generatedOn() == null ? null
                        : response.generatedOn().atZone(ZoneOffset.UTC).toLocalDate());
        Boolean identificationVerified = null;
        if (response.result() != null) {
            builder.isIdentityMatch("MATCH".equals(response.result().matchOutcome()))
                    .identityMatchConfidence(response.result().matchConfidence());
            identificationVerified = "VALID".equals(response.result().identificationStatus());
        }
        if (response.error() != null) {
            builder.errorCode(response.error().code()).errorMessage(response.error().message());
        }
        return BureauCallOutcome.success(partyId, builder.build(), identificationVerified);
    }

    private Identification firstIdentification(Party party) {
        return party.getIdentifications() == null || party.getIdentifications().isEmpty()
                ? null : party.getIdentifications().get(0);
    }

    private Address residentialAddress(Party party) {
        if (party.getAddresses() == null) {
            return null;
        }
        return party.getAddresses().stream()
                .filter(a -> a.getAddressType() == AddressType.Residential)
                .findFirst().orElse(null);
    }
}
