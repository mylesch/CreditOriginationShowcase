package com.cos.domain.model;

import com.cos.domain.enums.EmailType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Email {
    private String emailId;
    private EmailType emailType;
    private String email;
    private Boolean isPrimary;
}
