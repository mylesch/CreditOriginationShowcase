package com.cos.domain.model;

import com.cos.domain.enums.ErrorSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationError {
    private String errorId;
    private String code;
    private String message;
    private ErrorSource source;
    private String serviceName;
    private String partyId;
    private LocalDateTime occurredOn;
    private Boolean isResolved;
    private Integer retryCount;
}
