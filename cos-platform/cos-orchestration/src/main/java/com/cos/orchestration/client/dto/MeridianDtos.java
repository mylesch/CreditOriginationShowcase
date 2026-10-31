package com.cos.orchestration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for Meridian Bureau - Credit Report. */
public final class MeridianDtos {

    private MeridianDtos() {
    }

    public record Request(String subjectType, Subject subject, String reportType, String reference) {
        public record Subject(String firstName, String lastName, LocalDate dateOfBirth, String nationalIdNumber,
                               String companyRegistrationNumber) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String reference, LocalDate reportDate, Boolean recordFound, Score score,
                            List<LiabilityDto> liabilities, Integer tradingHistoryMonths, String status,
                            ApiError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(BigDecimal value, Integer scaleMin, Integer scaleMax, String scoreVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LiabilityDto(String accountRef, String category, BigDecimal balance, BigDecimal limit,
                                BigDecimal monthlyRepayment, BigDecimal arrearsAmount, Boolean isJointAccount,
                                LocalDate startDate) {
    }
}
