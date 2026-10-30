package com.cos.decision.engine;

import com.cos.domain.enums.DecisionStatus;
import com.cos.domain.enums.DocumentPurpose;
import com.cos.domain.enums.DocumentTypeCode;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.SubmittedDocumentStatus;
import com.cos.domain.model.Application;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import com.cos.domain.model.RequiredDocument;
import com.cos.domain.model.RequiredDocumentType;
import com.cos.domain.model.SubmittedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Party Required Documents (Decision Workbook v0.2) - a ruleset-style decision (produces a list,
 * not a single enum from a row match), so it's hand-written Java rather than a Drools decision
 * table, per the implementation plan.
 *
 * <p>The income-clarity-driven conditions (3 and 4 in the workbook's pseudocode) read
 * {@code PartyDecisions.incomeClarity}, which is produced by the Income Clarity Assessment
 * decision - not yet implemented in this slice. Until that lands, those two conditions simply
 * never fire here (incomeClarity is null), rather than guessing at a result.
 */
@Component
public class PartyRequiredDocumentsEvaluator {

    public List<RequiredDocument> evaluate(Application application, Party party) {
        List<RequiredDocument> required = new ArrayList<>();
        PartyDecisions decisions = party.getDecisions();
        PartyType partyType = party.getPartyType();

        boolean kycReferred = decisions != null && decisions.getCustomerKYCStatus() == DecisionStatus.REFER;
        boolean identificationUnverified = party.getIdentifications() != null
                && party.getIdentifications().stream().anyMatch(id -> !Boolean.TRUE.equals(id.getIsVerified()));
        if (partyType == PartyType.Consumer && (kycReferred || identificationUnverified)) {
            required.add(requiredDocument(party.getPartyId(), DocumentPurpose.KYC, DocumentTypeCode.ProofOfIdentity));
        }

        boolean kybReferred = decisions != null && decisions.getCustomerKYBStatus() == DecisionStatus.REFER;
        if (partyType == PartyType.Business && kybReferred) {
            required.add(requiredDocument(party.getPartyId(), DocumentPurpose.KYB,
                    DocumentTypeCode.CompanyRegistrationCertificate, DocumentTypeCode.BeneficialOwnerDeclaration));
        }

        boolean incomeClarityRequired = decisions != null && decisions.getIncomeClarity() != null
                && Boolean.TRUE.equals(decisions.getIncomeClarity().getIsIncomeClarityRequired());
        if (partyType == PartyType.Consumer && incomeClarityRequired) {
            required.add(requiredDocument(party.getPartyId(), DocumentPurpose.POI,
                    DocumentTypeCode.BankStatement, DocumentTypeCode.Payslip));
        }
        if (partyType == PartyType.Business && incomeClarityRequired) {
            required.add(requiredDocument(party.getPartyId(), DocumentPurpose.POI, DocumentTypeCode.FinancialStatements));
        }

        return removeAlreadySatisfied(required, application, party);
    }

    private List<RequiredDocument> removeAlreadySatisfied(List<RequiredDocument> required, Application application, Party party) {
        if (application.getDocuments() == null) {
            return required;
        }
        Set<DocumentTypeCode> verifiedTypes = application.getDocuments().stream()
                .filter(d -> party.getPartyId().equals(d.getPartyId()))
                .filter(d -> d.getStatus() == SubmittedDocumentStatus.Verified)
                .map(SubmittedDocument::getDocumentType)
                .collect(Collectors.toSet());

        return required.stream()
                .filter(rd -> rd.getSupportedDocumentTypes().stream()
                        .noneMatch(t -> verifiedTypes.contains(t.getType())))
                .collect(Collectors.toList());
    }

    private RequiredDocument requiredDocument(String partyId, DocumentPurpose purpose, DocumentTypeCode... types) {
        List<RequiredDocumentType> supported = new ArrayList<>();
        for (DocumentTypeCode type : types) {
            supported.add(RequiredDocumentType.builder().type(type).build());
        }
        return RequiredDocument.builder()
                .partyId(partyId)
                .documentPurpose(purpose)
                .supportedDocumentTypes(supported)
                .build();
    }
}
