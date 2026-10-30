package com.cos.domain.model;

import com.cos.domain.enums.AffordabilityStatus;
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
public class Affordability {
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal totalLiabilityRepayments;
    private BigDecimal disposableIncome;
    private BigDecimal requestedRepayment;
    private AffordabilityStatus affordabilityStatus;
    private List<DecisionReason> reasonCodes;
}
