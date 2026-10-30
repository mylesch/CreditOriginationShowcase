package com.cos.domain.model;

import com.cos.domain.enums.ResidentialStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Person {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String nationality;
    private ResidentialStatus residentialStatus;
    private Integer numberOfDependents;
    private List<Employment> employments;
    private List<Income> declaredIncomes;
    private List<Expense> declaredExpenses;
    private List<Liability> liabilities;
}
