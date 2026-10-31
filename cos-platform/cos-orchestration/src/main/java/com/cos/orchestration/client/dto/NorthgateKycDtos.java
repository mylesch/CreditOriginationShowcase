package com.cos.orchestration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for Northgate Identity Services - Individual Verification (KYC). */
public final class NorthgateKycDtos {

    private NorthgateKycDtos() {
    }

    public record Request(String requestId, Subject subject, String consentReference) {
        public record Subject(String firstName, String lastName, LocalDate dateOfBirth, String nationality,
                               Identification identification, Address address) {
        }

        public record Identification(String type, String number, String issuingCountry, LocalDate expiryDate) {
        }

        public record Address(String line1, String city, String postalCode, String country) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String requestId, String status, Result result, String subjectReference,
                            Instant generatedOn, ApiError error) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Result(String matchOutcome, BigDecimal matchConfidence, List<String> matchedFields,
                              String identificationStatus, Boolean watchlistChecked) {
        }
    }
}
