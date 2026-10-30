package com.cos.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncomeClarityAssessment {
    private BigDecimal declaredIncome;
    private BigDecimal estimatedRepayment;
    private BigDecimal incomeToRepaymentRatio;
    private Boolean isIncomeClarityRequired;
    private List<DecisionReason> reasonCodes;
}
