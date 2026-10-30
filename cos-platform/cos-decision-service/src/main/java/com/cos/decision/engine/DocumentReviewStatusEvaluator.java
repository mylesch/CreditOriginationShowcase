package com.cos.decision.engine;

import com.cos.domain.model.Application;
import com.cos.domain.model.DocumentExtraction;
import com.cos.domain.model.DocumentReviewStatus;
import com.cos.domain.model.SubmittedDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Document Review Status (Decision Workbook v0.2) - ruleset-style, hand-written Java.
 *
 * <p>DocumentExtraction is not nested under Application in the BOM (it's correlated to
 * SubmittedDocument only by documentId - a known BOM gap), so the orchestration layer supplies
 * the extraction results for this Application's documents separately.
 */
@Component
public class DocumentReviewStatusEvaluator {

    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.85");

    public DocumentReviewStatus evaluate(Application application, List<DocumentExtraction> extractions) {
        List<SubmittedDocument> documents = application.getDocuments() == null ? List.of() : application.getDocuments();
        Map<String, DocumentExtraction> byDocumentId = extractions == null ? Map.of()
                : extractions.stream().collect(Collectors.toMap(DocumentExtraction::getDocumentId, e -> e, (a, b) -> a));

        boolean classificationComplete = documents.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsClassificationRequired()))
                .allMatch(d -> Boolean.TRUE.equals(d.getIsClassified()));

        boolean verificationComplete = documents.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsClassified()))
                .allMatch(d -> byDocumentId.containsKey(d.getDocumentId()));

        boolean lowConfidence = documents.stream()
                .anyMatch(d -> d.getClassificationConfidence() != null
                        && d.getClassificationConfidence().compareTo(CONFIDENCE_THRESHOLD) < 0);
        boolean failedExtraction = extractions != null && extractions.stream()
                .anyMatch(e -> Boolean.FALSE.equals(e.getIsNameMatch()) || Boolean.FALSE.equals(e.getIsIdentityMatch()));

        return DocumentReviewStatus.builder()
                .isAutoClassificationComplete(classificationComplete)
                .isAutoVerificationComplete(verificationComplete)
                .requiresManualReview(lowConfidence || failedExtraction)
                .build();
    }
}
