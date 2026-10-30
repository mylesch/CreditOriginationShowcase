package com.cos.domain.model;

import com.cos.domain.enums.OfferType;
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
public class Offer {
    private String offerId;
    private OfferType type;
    private BigDecimal amount;
    private BigDecimal creditLimit;
    private BigDecimal creditAllocation;
    private Integer termMonths;
    private BigDecimal depositAmount;
    private BigDecimal depositPct;
    private Boolean isApprovalRequired;
    private Boolean isPOIRequired;
    private Boolean isDebitOrderRequired;
}
