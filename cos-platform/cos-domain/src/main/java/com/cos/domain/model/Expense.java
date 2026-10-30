package com.cos.domain.model;

import com.cos.domain.enums.ExpenseType;
import com.cos.domain.enums.Frequency;
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
public class Expense {
    private String expenseId;
    private ExpenseType type;
    private BigDecimal amount;
    private Frequency frequency;
}
