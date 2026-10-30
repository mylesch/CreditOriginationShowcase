package com.cos.domain.model;

import com.cos.domain.enums.DocumentTypeCode;
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
public class RequiredDocumentType {
    private DocumentTypeCode type;
    private Boolean isClassificationRequired;
    private Integer maxAgeDays;
}
