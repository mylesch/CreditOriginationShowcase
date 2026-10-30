package com.cos.decision.engine;

import com.cos.domain.enums.ApplicationRouting;
import com.cos.domain.enums.BankAccountValidity;
import com.cos.domain.enums.BureauCheckType;
import com.cos.domain.enums.DecisionStatus;
import com.cos.domain.enums.DocumentPurpose;
import com.cos.domain.enums.DocumentTypeCode;
import com.cos.domain.enums.PartyType;
import com.cos.domain.enums.RequestType;
import com.cos.domain.enums.Frequency;
import com.cos.domain.enums.IncomeSource;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationDecisions;
import com.cos.domain.model.BureauResponse;
import com.cos.domain.model.Income;
import com.cos.domain.model.IncomeClarityAssessment;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import com.cos.domain.model.Person;
import com.cos.domain.model.ProductRequest;
import com.cos.domain.model.RequiredDocument;
import com.cos.domain.model.RequiredDocumentType;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Application-scope decisions: External Data Required, Required Documents (App) and
 * Application Routing directly (including the FULL_ORIGINATION combined credit+bank rows fixed
 * in Decision Workbook v0.2). Affordability/Credit Eligibility (App) are exercised indirectly
 * through Application Routing, since routing is the one a caller actually observes.
 */
class ApplicationRuleEngineTest {

    private final ApplicationRuleEngine engine = new ApplicationRuleEngine(
            KieServices.Factory.get().getKieClasspathContainer(),
            new ExternalDataRequiredEvaluator(),
            new DocumentReviewStatusEvaluator(),
            new RequiredDocumentsAppEvaluator(),
            new NetIncomeEvaluator());

    @Test
    void externalDataRequired_consumerCreditCheckWithNoBureauResponsesRequestsThree() {
        Application app = baseApplication(RequestType.CREDIT_CHECK);
        app.setParties(List.of(Party.builder().partyId("P1").partyType(PartyType.Consumer).build()));

        ApplicationDecisions decisions = engine.evaluate(app, List.of());

        assertTrue(decisions.getExternalDataRequired().getIsBureauRequired());
        assertEquals(3, decisions.getExternalDataRequired().getBureauDataRequests().size());
        assertEquals(ApplicationRouting.EXTERNAL_DATA_REQUIRED, decisions.getApplicationRouting());
    }

    @Test
    void externalDataRequired_allChecksPresentMeansNoneRequired() {
        Application app = baseApplication(RequestType.CREDIT_CHECK);
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .bureauResponses(List.of(
                        BureauResponse.builder().bureauResponseId("B1").checkType(BureauCheckType.KYC).build(),
                        BureauResponse.builder().bureauResponseId("B2").checkType(BureauCheckType.ConsumerCredit).build(),
                        BureauResponse.builder().bureauResponseId("B3").checkType(BureauCheckType.Compliance).build()))
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.APPROVE).build())
                .build();
        app.setParties(List.of(party));

        ApplicationDecisions decisions = engine.evaluate(app, List.of());

        assertFalse(decisions.getExternalDataRequired().getIsBureauRequired());
    }

    @Test
    void requiredDocumentsApp_aggregatesAndDedupesAcrossParties() {
        Application app = baseApplication(RequestType.CREDIT_CHECK);
        RequiredDocument doc = RequiredDocument.builder().partyId("P1").documentPurpose(DocumentPurpose.KYC)
                .supportedDocumentTypes(List.of(RequiredDocumentType.builder().type(DocumentTypeCode.ProofOfIdentity).build()))
                .build();
        Party party1 = Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .bureauResponses(fullBureauResponses())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.REFER)
                        .requiredDocuments(List.of(doc, doc)).build())
                .build();
        app.setParties(List.of(party1));

        ApplicationDecisions decisions = engine.evaluate(app, List.of());

        assertEquals(1, decisions.getRequiredDocuments().size());
        assertEquals(ApplicationRouting.GET_DOCUMENTS, decisions.getApplicationRouting());
    }

    @Test
    void routing_cancelledWinsOverEverythingElse() {
        Application app = baseApplication(RequestType.CREDIT_CHECK);
        app.setIsCancelled(true);
        app.setParties(List.of(creditCleanParty()));

        assertEquals(ApplicationRouting.CANCEL, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_creditCheckDeclineRoutesToCreditDecline() {
        Application app = baseApplication(RequestType.CREDIT_CHECK);
        app.setParties(List.of(Party.builder().partyId("P1").partyType(PartyType.Consumer)
                .bureauResponses(fullBureauResponses())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.DECLINE).build())
                .build()));

        assertEquals(ApplicationRouting.CREDIT_DECLINE, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_creditCheckApproveRoutesToCreditApprove() {
        Application app = baseApplication(RequestType.CREDIT_CHECK);
        app.setParties(List.of(creditCleanParty()));

        assertEquals(ApplicationRouting.CREDIT_APPROVE, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_bankCheckApproveRoutesToBankApprove() {
        Application app = baseApplication(RequestType.BANK_CHECK);
        Party party = creditCleanParty();
        party.getDecisions().setBankAccountValidity(BankAccountValidity.APPROVE);
        app.setParties(List.of(party));

        assertEquals(ApplicationRouting.BANK_APPROVE, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_bankCheckReferRoutesToBankRefer() {
        Application app = baseApplication(RequestType.BANK_CHECK);
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer).isPrimary(true)
                .bureauResponses(fullBureauResponses())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.APPROVE)
                        .bankAccountValidity(BankAccountValidity.REFER).build())
                .build();
        app.setParties(List.of(party));

        assertEquals(ApplicationRouting.BANK_REFER, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    // -- FULL_ORIGINATION: the Decision Workbook v0.2 fix under test -------------------------

    @Test
    void routing_fullOrigination_creditCleanButBankReferStillRefersOnBank() {
        Application app = baseApplication(RequestType.FULL_ORIGINATION);
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer).isPrimary(true)
                .bureauResponses(fullBureauResponses())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.APPROVE)
                        .bankAccountValidity(BankAccountValidity.REFER).build())
                .build();
        app.setParties(List.of(party));

        // Before the v0.2 fix, this combination fell through to CREDIT_APPROVE, silently
        // ignoring the failed bank check - that was exactly the bug being fixed.
        assertEquals(ApplicationRouting.BANK_REFER, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_fullOrigination_creditDeclineWinsRegardlessOfBank() {
        Application app = baseApplication(RequestType.FULL_ORIGINATION);
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer).isPrimary(true)
                .bureauResponses(fullBureauResponses())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.DECLINE)
                        .bankAccountValidity(BankAccountValidity.APPROVE).build())
                .build();
        app.setParties(List.of(party));

        assertEquals(ApplicationRouting.CREDIT_DECLINE, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_fullOrigination_bothCleanRoutesToBankApprove() {
        Application app = baseApplication(RequestType.FULL_ORIGINATION);
        Party party = creditCleanParty();
        party.getDecisions().setBankAccountValidity(BankAccountValidity.APPROVE);
        app.setParties(List.of(party));

        assertEquals(ApplicationRouting.BANK_APPROVE, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    @Test
    void routing_fullOrigination_creditReferWithCleanBankRoutesToCreditRefer() {
        Application app = baseApplication(RequestType.FULL_ORIGINATION);
        Party party = Party.builder().partyId("P1").partyType(PartyType.Consumer).isPrimary(true)
                .bureauResponses(fullBureauResponses())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.REFER)
                        .bankAccountValidity(BankAccountValidity.APPROVE).build())
                .build();
        app.setParties(List.of(party));

        assertEquals(ApplicationRouting.CREDIT_REFER, engine.evaluate(app, List.of()).getApplicationRouting());
    }

    /**
     * A party with comfortable declared income and income clarity already satisfied, so
     * Affordability resolves to PASS rather than REQUIRES_DOCUMENTS (the default when there is
     * no income data at all) - needed by every test that expects an app-level creditEligibility
     * of exactly APPROVE, since Credit Eligibility (App) folds affordability in.
     */
    private Party creditCleanParty() {
        return Party.builder().partyId("P1").partyType(PartyType.Consumer).isPrimary(true)
                .bureauResponses(fullBureauResponses())
                .person(Person.builder()
                        .declaredIncomes(List.of(Income.builder().incomeId("INC-1")
                                .source(IncomeSource.Employment).amount(new BigDecimal("50000"))
                                .frequency(Frequency.Monthly).build()))
                        .build())
                .decisions(PartyDecisions.builder().creditEligibility(DecisionStatus.APPROVE)
                        .incomeClarity(IncomeClarityAssessment.builder().isIncomeClarityRequired(false).build())
                        .build())
                .build();
    }

    private List<BureauResponse> fullBureauResponses() {
        return List.of(
                BureauResponse.builder().bureauResponseId("B1").checkType(BureauCheckType.KYC).build(),
                BureauResponse.builder().bureauResponseId("B2").checkType(BureauCheckType.ConsumerCredit).build(),
                BureauResponse.builder().bureauResponseId("B3").checkType(BureauCheckType.Compliance).build(),
                BureauResponse.builder().bureauResponseId("B4").checkType(BureauCheckType.BankCheck).build());
    }

    private Application baseApplication(RequestType requestType) {
        Application app = Application.builder()
                .applicationId("APP-1")
                .requestType(requestType)
                .isCancelled(false)
                .build();
        app.setProductRequests(List.of(ProductRequest.builder()
                .productRequestId("PR-1").requestedAmount(new BigDecimal("5000")).termMonths(12).build()));
        return app;
    }
}
