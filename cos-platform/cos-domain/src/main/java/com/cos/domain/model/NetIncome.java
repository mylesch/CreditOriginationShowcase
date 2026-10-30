package com.cos.domain.model;

import com.cos.domain.enums.IncomeBase;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetIncome {
    private BigDecimal declaredIncome;
    private BigDecimal detectedIncome;
    private BigDecimal confirmedIncome;
    private BigDecimal totalIncome;
    private IncomeBase incomeBase;
}
