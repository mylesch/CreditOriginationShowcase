package com.cos.domain.model;

import com.cos.domain.enums.EmploymentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Employment {
    private String employmentId;
    private String employerName;
    private EmploymentType employmentType;
    private String industry;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Boolean isCurrent;
    private Integer monthsAtEmployment;
}
