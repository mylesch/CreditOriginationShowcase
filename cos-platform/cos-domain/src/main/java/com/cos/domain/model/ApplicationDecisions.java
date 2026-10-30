package com.cos.domain.model;

import com.cos.domain.enums.ApplicationRouting;
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
public class ApplicationDecisions {
    private DecisionStatus creditEligibility;
    private ExternalDataRequired externalDataRequired;
    private List<RequiredDocument> requiredDocuments;
    private DocumentReviewStatus documentReviewStatus;
    private NetIncome netIncome;
    private Affordability affordability;
    private ApplicationRouting applicationRouting;
    private List<DecisionReason> reasons;
}
