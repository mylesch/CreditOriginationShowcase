package com.cos.domain.model;

import com.cos.domain.enums.DocumentTypeCode;
import com.cos.domain.enums.SubmittedDocumentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmittedDocument {
    private String documentId;
    private String partyId;
    private String requiredDocumentId;
    private DocumentTypeCode documentType;
    private String fileName;
    private String link;
    private SubmittedDocumentStatus status;
    private Boolean isClassified;
    private Boolean isClassificationRequired;
    private Boolean isRequired;
    private LocalDateTime addedOn;
    private BigDecimal classificationConfidence;
}
