package com.cos.domain.model;

import com.cos.domain.enums.PhoneType;
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
public class Phone {
    private String phoneId;
    private PhoneType phoneType;
    private String countryCode;
    private String number;
    private Boolean isPrimary;
}
