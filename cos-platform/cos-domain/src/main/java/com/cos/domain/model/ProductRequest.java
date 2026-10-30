package com.cos.domain.model;

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
public class ProductRequest {
    private String productRequestId;
    private String productCode;
    private BigDecimal requestedAmount;
    private Integer termMonths;
    private Offer approvedOffer;
    private Offer selectedOffer;
}
