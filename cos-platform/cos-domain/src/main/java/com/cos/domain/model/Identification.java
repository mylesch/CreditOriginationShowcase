package com.cos.domain.model;

import com.cos.domain.enums.IdentificationType;
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
public class Identification {
    private String identificationId;
    private IdentificationType identificationType;
    private String reference;
    private LocalDate issuedOn;
    private LocalDate expiresOn;
    private String placeIssued;
    private Boolean isVerified;
}
