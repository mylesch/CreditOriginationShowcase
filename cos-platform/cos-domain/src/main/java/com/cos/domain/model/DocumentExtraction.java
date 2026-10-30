package com.cos.domain.model;

import com.cos.domain.enums.DocumentTypeCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentExtraction {
    private String documentId;
    private DocumentTypeCode extractedDocumentType;
    private BigDecimal extractionConfidence;
    private String extractedIdNumber;
    private String extractedFullName;
    private BigDecimal extractedIncomeAmount;
    private LocalDate extractedIncomeDate;
    private String extractedAddress;
    private Boolean isNameMatch;
    private Boolean isIdentityMatch;

    // technical call-result tracking, added BOM v0.3
    private LocalDateTime processedOn;
    private Boolean isSuccessful;
    private String errorCode;
    private String errorMessage;
}
