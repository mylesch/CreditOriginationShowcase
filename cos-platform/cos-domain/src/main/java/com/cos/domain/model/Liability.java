package com.cos.domain.model;

import com.cos.domain.enums.LiabilityCategory;
import com.cos.domain.enums.LiabilitySource;
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
public class Liability {
    private String liabilityId;
    private LiabilityCategory category;
    private BigDecimal balance;
    private BigDecimal limit;
    private BigDecimal monthlyRepayment;
    private Boolean isJointAccount;
    private BigDecimal arrears;
    private LiabilitySource source;
}
