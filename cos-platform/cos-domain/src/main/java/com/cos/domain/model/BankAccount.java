package com.cos.domain.model;

import com.cos.domain.enums.BankAccountType;
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
public class BankAccount {
    private String bankAccountId;
    private BankAccountType accountType;
    private String accountNumber;
    private String bankName;
    private String branchCode;
    private String accountHolderName;
    private BigDecimal yearsWithBank;
    private Boolean isPrimary;
}
