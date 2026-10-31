package com.cos.orchestration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for Sentinel Compliance Screening - PEPs / Sanctions / Watchlist. */
public final class SentinelDtos {

    private SentinelDtos() {
    }

    public record Request(Subject subject, List<String> lists, String reference) {
        public record Subject(String fullName, LocalDate dateOfBirth, String nationality, String subjectType) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String reference, Instant screenedOn, List<Match> matches, Boolean overallMatch,
                            String status, ApiError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String list, BigDecimal matchScore, String matchedName, String category) {
    }
}
