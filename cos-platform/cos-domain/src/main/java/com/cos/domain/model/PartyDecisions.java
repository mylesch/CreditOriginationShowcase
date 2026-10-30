package com.cos.domain.model;

import com.cos.domain.enums.BankAccountValidity;
import com.cos.domain.enums.BankCheckResult;
import com.cos.domain.enums.BureauScoreBand;
import com.cos.domain.enums.CreditStatusOverrideValue;
import com.cos.domain.enums.DecisionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartyDecisions {
    private DecisionStatus customerKYCStatus;
    private DecisionStatus customerKYBStatus;
    private Boolean hasRepresentativeAuthority;
    private DecisionStatus customerPEPsStatus;
    private DecisionStatus customerCreditStatus;
    private CreditStatusOverrideValue creditStatusOverride;
    private BureauScoreBand bureauScoreBand;
    private BankCheckResult bankCheckResult;
    private BankAccountValidity bankAccountValidity;
    private IncomeClarityAssessment incomeClarity;
    private DecisionStatus creditEligibility;
    private List<RequiredDocument> requiredDocuments;
}
