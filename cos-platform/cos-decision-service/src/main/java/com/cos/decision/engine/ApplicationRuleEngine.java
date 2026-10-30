package com.cos.decision.engine;

import com.cos.domain.model.Affordability;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationDecisions;
import com.cos.domain.model.DocumentExtraction;
import com.cos.domain.model.DocumentReviewStatus;
import com.cos.domain.model.Expense;
import com.cos.domain.model.Liability;
import com.cos.domain.model.NetIncome;
import com.cos.domain.model.Party;
import com.cos.domain.model.ProductRequest;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evaluates every Application-scope decision once every Party has already been evaluated
 * (PartyRuleEngine). Four are ruleset-style (plain Java, run first since nothing downstream
 * needs a KieSession for them): External Data Required, Document Review Status, Required
 * Documents (App), Net Income. Three are table-style (Drools), run in a single Application-scope
 * KieSession: Affordability, Credit Eligibility (App), Application Routing.
 */
@Component
public class ApplicationRuleEngine {

    private final KieContainer kieContainer;
    private final ExternalDataRequiredEvaluator externalDataRequiredEvaluator;
    private final DocumentReviewStatusEvaluator documentReviewStatusEvaluator;
    private final RequiredDocumentsAppEvaluator requiredDocumentsAppEvaluator;
    private final NetIncomeEvaluator netIncomeEvaluator;

    public ApplicationRuleEngine(KieContainer kieContainer,
                                  ExternalDataRequiredEvaluator externalDataRequiredEvaluator,
                                  DocumentReviewStatusEvaluator documentReviewStatusEvaluator,
                                  RequiredDocumentsAppEvaluator requiredDocumentsAppEvaluator,
                                  NetIncomeEvaluator netIncomeEvaluator) {
        this.kieContainer = kieContainer;
        this.externalDataRequiredEvaluator = externalDataRequiredEvaluator;
        this.documentReviewStatusEvaluator = documentReviewStatusEvaluator;
        this.requiredDocumentsAppEvaluator = requiredDocumentsAppEvaluator;
        this.netIncomeEvaluator = netIncomeEvaluator;
    }

    public ApplicationDecisions evaluate(Application application, List<DocumentExtraction> documentExtractions) {
        ApplicationDecisions decisions = new ApplicationDecisions();

        decisions.setExternalDataRequired(externalDataRequiredEvaluator.evaluate(application));
        DocumentReviewStatus documentReviewStatus = documentReviewStatusEvaluator.evaluate(application, documentExtractions);
        decisions.setDocumentReviewStatus(documentReviewStatus);
        decisions.setRequiredDocuments(requiredDocumentsAppEvaluator.evaluate(application));
        NetIncome netIncome = netIncomeEvaluator.evaluate(application, documentExtractions, documentReviewStatus);
        decisions.setNetIncome(netIncome);

        Affordability affordability = buildAffordability(application, netIncome);

        try (KieSession session = kieContainer.newKieSession("applicationKSession")) {
            session.insert(application);
            session.insert(decisions);
            session.insert(netIncome);
            session.insert(affordability);
            if (application.getParties() != null) {
                application.getParties().forEach(session::insert);
            }
            session.fireAllRules();
        }
        decisions.setAffordability(affordability);

        // Run strictly after the above has finished and creditEligibility is fixed - see the
        // kmodule.xml / generate_decision_tables.py comments on why Application Routing is its
        // own KieBase/session rather than sharing the one above.
        try (KieSession session = kieContainer.newKieSession("routingKSession")) {
            session.insert(application);
            session.insert(decisions);
            if (application.getParties() != null) {
                application.getParties().forEach(session::insert);
            }
            session.fireAllRules();
        }
        return decisions;
    }

    private Affordability buildAffordability(Application application, NetIncome netIncome) {
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalLiabilityRepayments = BigDecimal.ZERO;
        if (application.getParties() != null) {
            for (Party party : application.getParties()) {
                if (party.getPerson() != null) {
                    if (party.getPerson().getDeclaredExpenses() != null) {
                        for (Expense expense : party.getPerson().getDeclaredExpenses()) {
                            totalExpenses = totalExpenses.add(
                                    AffordabilityMath.normaliseToMonthly(expense.getAmount(), expense.getFrequency()));
                        }
                    }
                    if (party.getPerson().getLiabilities() != null) {
                        totalLiabilityRepayments = totalLiabilityRepayments.add(sumRepayments(party.getPerson().getLiabilities()));
                    }
                }
                if (party.getCompany() != null && party.getCompany().getLiabilities() != null) {
                    totalLiabilityRepayments = totalLiabilityRepayments.add(sumRepayments(party.getCompany().getLiabilities()));
                }
            }
        }

        ProductRequest productRequest = (application.getProductRequests() == null || application.getProductRequests().isEmpty())
                ? null : application.getProductRequests().get(0);
        BigDecimal requestedRepayment = productRequest == null ? BigDecimal.ZERO
                : AffordabilityMath.monthlyInstalment(productRequest.getRequestedAmount(), productRequest.getTermMonths());

        BigDecimal totalIncome = netIncome.getTotalIncome() == null ? BigDecimal.ZERO : netIncome.getTotalIncome();
        BigDecimal disposableIncome = totalIncome.subtract(totalExpenses).subtract(totalLiabilityRepayments);

        return Affordability.builder()
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .totalLiabilityRepayments(totalLiabilityRepayments)
                .disposableIncome(disposableIncome)
                .requestedRepayment(requestedRepayment)
                .build();
    }

    private BigDecimal sumRepayments(List<Liability> liabilities) {
        return liabilities.stream()
                .map(Liability::getMonthlyRepayment)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
