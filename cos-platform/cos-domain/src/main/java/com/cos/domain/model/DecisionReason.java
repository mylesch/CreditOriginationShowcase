package com.cos.domain.model;

import com.cos.domain.enums.DecisionCheckCategory;
import com.cos.domain.enums.DecisionStatus;
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
public class DecisionReason {
    private String ruleId;
    private String decisionName;
    private String partyId;
    private DecisionStatus proposedStatus;
    private DecisionCheckCategory checkType;
    private String internalReason;
    private String externalReason;
    private Integer rank;
}
