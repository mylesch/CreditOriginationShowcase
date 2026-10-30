package com.cos.domain.model;

import com.cos.domain.enums.CompanyType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Company {
    private String legalName;
    private String tradingName;
    private String registrationNumber;
    private String taxNumber;
    private CompanyType companyType;
    private String industrySector;
    private LocalDate incorporationDate;
    private BigDecimal yearsTrading;
    private BigDecimal annualTurnover;
    private Integer numberOfEmployees;
    private String registeredAddressId;
    private List<Liability> liabilities;
}
