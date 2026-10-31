package com.cos.orchestration.client;

import com.cos.domain.enums.AddressType;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.enums.CompanyRegistrationStatus;
import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PersonReferenceRole;
import com.cos.domain.model.Address;
import com.cos.domain.model.Application;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Party;
import com.cos.domain.model.PersonReference;
import com.cos.orchestration.client.dto.NorthgateKybDtos.Request;
import com.cos.orchestration.client.dto.NorthgateKybDtos.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calls Northgate Identity Services - Company Verification (KYB). See the Integration
 * Workbook's "Northgate KYB API" tab and the Process Workbook's "Call Northgate KYB Service" tab.
 */
@Component
public class NorthgateKybClient {

    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), Set.of(502, 503, 504));

    private final RestClient restClient;
    private final RetryingCaller retryingCaller;

    public NorthgateKybClient(@Value("${cos.orchestration.bureaus.northgate.base-url}") String baseUrl,
                               RetryingCaller retryingCaller) {
        this.restClient = RestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        this.retryingCaller = retryingCaller;
    }

    public BureauCallOutcome verify(Application application, Party businessParty) {
        try {
            Response response = retryingCaller.execute("Northgate Identity Services (KYB)", "INT-NG-KYB-01",
                    RETRY_POLICY, () -> restClient.post()
                            .uri("/v1/companies/verify")
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .header("X-Client-Id", "cos-origination")
                            .body(buildRequest(application, businessParty))
                            .retrieve()
                            .body(Response.class));
            return mapResponse(businessParty.getPartyId(), response);
        } catch (IntegrationCallException e) {
            return BureauCallOutcome.failure(businessParty.getPartyId(), ApplicationErrors.fromIntegrationFailure(e));
        }
    }

    private Request buildRequest(Application application, Party businessParty) {
        Address registeredOffice = addressOfType(businessParty, AddressType.RegisteredOffice);
        Request.Subject subject = new Request.Subject(
                businessParty.getCompany() == null ? null : businessParty.getCompany().getLegalName(),
                businessParty.getCompany() == null ? null : businessParty.getCompany().getRegistrationNumber(),
                registeredOffice == null ? null : registeredOffice.getCountry(),
                businessParty.getCompany() == null || businessParty.getCompany().getCompanyType() == null
                        ? null : businessParty.getCompany().getCompanyType().name());

        Party representative = linkedRepresentative(application);
        Request.Representative representativeDto = representative == null || representative.getPerson() == null
                ? null
                : new Request.Representative(
                        representative.getPerson().getFirstName(),
                        representative.getPerson().getLastName(),
                        representative.getPerson().getDateOfBirth(),
                        representative.getIdentifications() == null || representative.getIdentifications().isEmpty()
                                ? null : representative.getIdentifications().get(0).getReference());

        return new Request(UUID.randomUUID().toString(), subject, representativeDto, application.getApplicationId());
    }

    private BureauCallOutcome mapResponse(String partyId, Response response) {
        boolean isSuccessful = "COMPLETED".equals(response.status()) && response.error() == null;
        BureauResponse.BureauResponseBuilder builder = BureauResponse.builder()
                .bureauName(BureauName.NORTHGATE_IDENTITY_SERVICES)
                .checkType(BureauCheckType.KYB)
                .correlationId(response.requestId())
                .isSuccessful(isSuccessful)
                .dateOfProvision(response.generatedOn() == null ? null
                        : response.generatedOn().atZone(ZoneOffset.UTC).toLocalDate());
        if (response.result() != null) {
            builder.isIdentityMatch("MATCH".equals(response.result().matchOutcome()))
                    .companyRegistrationStatus(toRegistrationStatus(response.result().registrationStatus()))
                    .businessStartDate(response.result().incorporationDate())
                    .directors(toDirectorReferences(response.result().directors()));
        }
        if (response.error() != null) {
            builder.errorCode(response.error().code()).errorMessage(response.error().message());
        }
        return BureauCallOutcome.success(partyId, builder.build());
    }

    private List<PersonReference> toDirectorReferences(List<Response.Director> directors) {
        if (directors == null) {
            return List.of();
        }
        return directors.stream().map(d -> PersonReference.builder()
                .fullName((d.firstName() == null ? "" : d.firstName()) + " " + (d.lastName() == null ? "" : d.lastName()))
                .dateOfBirth(d.dateOfBirth())
                .identificationNumber(d.identificationNumber())
                .role(toPersonReferenceRole(d.role()))
                .build()).toList();
    }

    private PersonReferenceRole toPersonReferenceRole(String role) {
        if (role == null) {
            return PersonReferenceRole.Other;
        }
        try {
            return PersonReferenceRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return PersonReferenceRole.Other;
        }
    }

    private CompanyRegistrationStatus toRegistrationStatus(String registrationStatus) {
        if (registrationStatus == null) {
            return CompanyRegistrationStatus.Unknown;
        }
        return switch (registrationStatus) {
            case "ACTIVE" -> CompanyRegistrationStatus.Active;
            case "DEREGISTERED" -> CompanyRegistrationStatus.Deregistered;
            case "IN_LIQUIDATION" -> CompanyRegistrationStatus.InLiquidation;
            default -> CompanyRegistrationStatus.Unknown;
        };
    }

    private Party linkedRepresentative(Application application) {
        if (application.getParties() == null) {
            return null;
        }
        return application.getParties().stream()
                .filter(p -> p.getPartyRole() == PartyRole.Representative
                        || p.getPartyRole() == PartyRole.Director
                        || p.getPartyRole() == PartyRole.BeneficialOwner)
                .findFirst().orElse(null);
    }

    private Address addressOfType(Party party, AddressType type) {
        if (party.getAddresses() == null) {
            return null;
        }
        return party.getAddresses().stream().filter(a -> a.getAddressType() == type).findFirst().orElse(null);
    }
}
