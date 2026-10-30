package com.cos.domain.model;

import com.cos.domain.enums.DocumentPurpose;
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
public class RequiredDocument {
    private String id;
    private String partyId;
    private DocumentPurpose documentPurpose;
    private String reasonCode;
    private List<RequiredDocumentType> supportedDocumentTypes;
}
