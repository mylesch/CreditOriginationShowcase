package com.cos.domain.model;

import com.cos.domain.enums.BankCheckResultCode;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
import com.cos.domain.enums.CompanyRegistrationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BureauResponse {
    private String bureauResponseId;
    private BureauName bureauName;
    private BureauCheckType checkType;
    private LocalDate dateOfProvision;
    private BigDecimal numericBureauScore;
    private String nonNumericBureauScore;
    private Boolean isIdentityMatch;
    private BigDecimal identityMatchConfidence;
    private Boolean isPEPsMatch;
    private BankCheckResultCode bankCheckResult;
    private LocalDate businessStartDate;
    private CompanyRegistrationStatus companyRegistrationStatus;
    private List<PersonReference> directors;

    // technical call-result tracking, added BOM v0.3 - independent of the business outcome above
    private String correlationId;
    private Boolean isSuccessful;
    private String errorCode;
    private String errorMessage;
}
