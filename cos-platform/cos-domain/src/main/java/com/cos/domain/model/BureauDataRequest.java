package com.cos.domain.model;

import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.BureauName;
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
public class BureauDataRequest {
    private String partyId;
    private BureauCheckType checkType;
    private BureauName bureau;
    private String service;
}
