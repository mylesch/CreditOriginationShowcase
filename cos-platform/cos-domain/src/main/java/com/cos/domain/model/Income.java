package com.cos.domain.model;

import com.cos.domain.enums.Frequency;
import com.cos.domain.enums.IncomeSource;
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
public class Income {
    private String incomeId;
    private IncomeSource source;
    private String type;
    private BigDecimal amount;
    private Frequency frequency;
    private String employmentId;
}
