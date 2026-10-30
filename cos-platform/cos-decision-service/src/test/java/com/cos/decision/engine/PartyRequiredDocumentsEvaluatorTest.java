package com.cos.decision.engine;

import com.cos.domain.enums.DecisionStatus;
import com.cos.domain.enums.DocumentPurpose;
import com.cos.domain.enums.DocumentTypeCode;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.SubmittedDocumentStatus;
import com.cos.domain.model.Application;
import com.cos.domain.model.Identification;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import com.cos.domain.model.RequiredDocument;
import com.cos.domain.model.SubmittedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyRequiredDocumentsEvaluatorTest {

    private final PartyRequiredDocumentsEvaluator evaluator = new PartyRequiredDocumentsEvaluator();

    @Test
    void consumerWithReferredKycNeedsProofOfIdentity() {
        Party party = Party.builder()
                .partyId("P1").partyType(PartyType.Consumer)
                .decisions(PartyDecisions.builder().customerKYCStatus(DecisionStatus.REFER).build())
                .build();

        List<RequiredDocument> required = evaluator.evaluate(Application.builder().build(), party);

        assertEquals(1, required.size());
        assertEquals(DocumentPurpose.KYC, required.get(0).getDocumentPurpose());
        assertEquals(DocumentTypeCode.ProofOfIdentity, required.get(0).getSupportedDocumentTypes().get(0).getType());
    }

    @Test
    void consumerWithUnverifiedIdentificationAlsoNeedsProofOfIdentity() {
        Party party = Party.builder()
                .partyId("P1").partyType(PartyType.Consumer)
                .decisions(PartyDecisions.builder().customerKYCStatus(DecisionStatus.APPROVE).build())
                .identifications(List.of(Identification.builder().identificationId("I1").isVerified(false).build()))
                .build();

        List<RequiredDocument> required = evaluator.evaluate(Application.builder().build(), party);

        assertEquals(1, required.size());
        assertEquals(DocumentPurpose.KYC, required.get(0).getDocumentPurpose());
    }

    @Test
    void businessWithReferredKybNeedsCompanyDocs() {
        Party party = Party.builder()
                .partyId("P1").partyType(PartyType.Business)
                .decisions(PartyDecisions.builder().customerKYBStatus(DecisionStatus.REFER).build())
                .build();

        List<RequiredDocument> required = evaluator.evaluate(Application.builder().build(), party);

        assertEquals(1, required.size());
        assertEquals(DocumentPurpose.KYB, required.get(0).getDocumentPurpose());
        assertEquals(2, required.get(0).getSupportedDocumentTypes().size());
    }

    @Test
    void approvedConsumerWithVerifiedIdNeedsNoDocuments() {
        Party party = Party.builder()
                .partyId("P1").partyType(PartyType.Consumer)
                .decisions(PartyDecisions.builder().customerKYCStatus(DecisionStatus.APPROVE).build())
                .identifications(List.of(Identification.builder().identificationId("I1").isVerified(true).build()))
                .build();

        List<RequiredDocument> required = evaluator.evaluate(Application.builder().build(), party);

        assertTrue(required.isEmpty());
    }

    @Test
    void requirementAlreadySatisfiedByVerifiedSubmissionIsRemoved() {
        Party party = Party.builder()
                .partyId("P1").partyType(PartyType.Consumer)
                .decisions(PartyDecisions.builder().customerKYCStatus(DecisionStatus.REFER).build())
                .build();
        Application application = Application.builder()
                .documents(List.of(SubmittedDocument.builder()
                        .documentId("D1").partyId("P1")
                        .documentType(DocumentTypeCode.ProofOfIdentity)
                        .status(SubmittedDocumentStatus.Verified)
                        .build()))
                .build();

        List<RequiredDocument> required = evaluator.evaluate(application, party);

        assertTrue(required.isEmpty());
    }
}
