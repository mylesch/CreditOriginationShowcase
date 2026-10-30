package com.cos.domain.model;

import com.cos.domain.enums.PersonReferenceRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Minimal reference to an individual returned by a bureau alongside a company record
 * (BureauResponse.directors[]) - defined in BOM v0.5; previously used as a data type with no
 * entity definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonReference {
    private String fullName;
    private LocalDate dateOfBirth;
    private String identificationNumber;
    private PersonReferenceRole role;
}
