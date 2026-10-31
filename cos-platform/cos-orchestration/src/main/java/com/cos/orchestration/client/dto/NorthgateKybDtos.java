package com.cos.orchestration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for Northgate Identity Services - Company Verification (KYB). */
public final class NorthgateKybDtos {

    private NorthgateKybDtos() {
    }

    public record Request(String requestId, Subject subject, Representative representative, String consentReference) {
        public record Subject(String legalName, String registrationNumber, String registrationCountry,
                               String companyType) {
        }

        public record Representative(String firstName, String lastName, LocalDate dateOfBirth,
                                       String identificationNumber) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String requestId, String status, Result result, Instant generatedOn, ApiError error) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Result(String matchOutcome, BigDecimal matchConfidence, String registrationStatus,
                              LocalDate incorporationDate, List<Director> directors, List<BeneficialOwner> beneficialOwners,
                              Boolean representativeAuthorityConfirmed) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Director(String firstName, String lastName, LocalDate dateOfBirth, String identificationNumber,
                                String role) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record BeneficialOwner(String firstName, String lastName, BigDecimal ownershipPercentage) {
        }
    }
}
