package com.cos.domain.model;

import com.cos.domain.enums.ApplicationStatus;
import com.cos.domain.enums.Channel;
import com.cos.domain.enums.ManualBankDecision;
import com.cos.domain.enums.ManualCreditDecision;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.RequestType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Application {
    private String applicationId;
    private LocalDateTime applicationTimestamp;
    private LocalDateTime submittedOn;
    private ApplicationStatus status;
    private Channel channel;
    private String countryCode;
    private String currency;
    private RequestType requestType;
    private PartyType customerType;
    private List<ProductRequest> productRequests;
    private List<Party> parties;
    private List<SubmittedDocument> documents;
    private ManualCreditDecision manualCreditDecision;
    private ManualBankDecision manualBankDecision;
    private Boolean isCancelled;
    private Boolean hasCreditApproval;
    private ApplicationDecisions decisions;
    private List<ApplicationError> errors;
    private Integer drsCycleCount;
}
