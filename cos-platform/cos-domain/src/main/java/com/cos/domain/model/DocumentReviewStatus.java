package com.cos.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentReviewStatus {
    private Boolean isAutoClassificationComplete;
    private Boolean isAutoVerificationComplete;
    private Boolean requiresManualReview;
    private List<DecisionReason> reasons;
}
