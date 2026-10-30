package com.cos.decision.engine;

import com.cos.domain.enums.PartyRole;
import com.cos.domain.model.Application;
import com.cos.domain.model.Employment;
import com.cos.domain.model.IncomeClarityAssessment;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import com.cos.domain.model.PersonReference;
import com.cos.domain.model.ProductRequest;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates every table-style, Party-scope decision for one Party, in a fresh KieSession scoped
 * to that party alone: Customer KYC/KYB Status, Customer PEPs Status, Bureau Score Band,
 * Customer Credit Status, Credit Status Override, Bank Check Result, Bank Account Validity,
 * Income Clarity Assessment, Party Credit Eligibility.
 *
 * <p>Running one party per session (rather than one session for the whole Application) means no
 * BureauResponse/Liability/Identification needs a partyId correlation key to be matched
 * correctly - the session only ever contains one party's facts - mirroring the Decision
 * Workbook's own framing of these decisions as "evaluated once per party".
 *
 * <p>Two inputs can't be derived purely by rules inside that single-party session and are
 * precomputed here instead: {@code hasRepresentativeAuthority} (Customer KYB Status) needs to
 * see a *different* party (the linked Representative) on the same Application, and Income
 * Clarity Assessment's declaredIncome/estimatedRepayment/incomeToRepaymentRatio are arithmetic,
 * not decision logic. Both are written onto their result fact before it is inserted, and the
 * decision tables only read them.
 */
@Component
public class PartyRuleEngine {

    private final KieContainer kieContainer;

    public PartyRuleEngine(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public PartyDecisions evaluate(Application application, Party party) {
        PartyDecisions decisions = new PartyDecisions();
        decisions.setHasRepresentativeAuthority(hasRepresentativeAuthority(application, party));

        IncomeClarityAssessment incomeClarity = buildIncomeClarityAssessment(application, party);
        Employment representativeEmployment = representativeEmployment(party);

        try (KieSession session = kieContainer.newKieSession("partyKSession")) {
            session.insert(application);
            session.insert(party);
            session.insert(decisions);
            session.insert(incomeClarity);
            if (representativeEmployment != null) {
                session.insert(representativeEmployment);
            }

            Optional.ofNullable(party.getPerson()).ifPresent(session::insert);
            Optional.ofNullable(party.getCompany()).ifPresent(session::insert);

            insertAll(session, party.getIdentifications());
            insertAll(session, party.getBureauResponses());
            if (party.getPerson() != null) {
                insertAll(session, party.getPerson().getLiabilities());
            }
            if (party.getCompany() != null) {
                insertAll(session, party.getCompany().getLiabilities());
            }

            session.fireAllRules();
        }
        decisions.setIncomeClarity(incomeClarity);
        return decisions;
    }

    /**
     * True when a linked Representative party's identification reference matches one of the
     * Business party's BureauResponse[checkType=KYB].directors[] entries. Only meaningful for a
     * Business party; returns false (not null) otherwise, consistent with the decision table
     * reading it as a plain boolean.
     */
    private boolean hasRepresentativeAuthority(Application application, Party party) {
        if (party.getBureauResponses() == null || application.getParties() == null) {
            return false;
        }
        List<PersonReference> directors = party.getBureauResponses().stream()
                .filter(br -> br.getDirectors() != null)
                .flatMap(br -> br.getDirectors().stream())
                .toList();
        if (directors.isEmpty()) {
            return false;
        }
        return application.getParties().stream()
                .filter(p -> p.getPartyRole() == PartyRole.Representative)
                .filter(p -> p.getIdentifications() != null)
                .flatMap(p -> p.getIdentifications().stream())
                .map(id -> id.getReference())
                .filter(reference -> reference != null)
                .anyMatch(reference -> directors.stream()
                        .anyMatch(d -> reference.equals(d.getIdentificationNumber())));
    }

    private IncomeClarityAssessment buildIncomeClarityAssessment(Application application, Party party) {
        BigDecimal declaredIncome = declaredIncome(party);
        ProductRequest productRequest = (application.getProductRequests() == null || application.getProductRequests().isEmpty())
                ? null : application.getProductRequests().get(0);
        BigDecimal estimatedRepayment = productRequest == null ? BigDecimal.ZERO
                : AffordabilityMath.monthlyInstalment(productRequest.getRequestedAmount(), productRequest.getTermMonths());
        BigDecimal ratio = estimatedRepayment.signum() == 0 ? BigDecimal.ZERO
                : declaredIncome.divide(estimatedRepayment, 4, java.math.RoundingMode.HALF_UP);

        return IncomeClarityAssessment.builder()
                .declaredIncome(declaredIncome)
                .estimatedRepayment(estimatedRepayment)
                .incomeToRepaymentRatio(ratio)
                .build();
    }

    private BigDecimal declaredIncome(Party party) {
        if (party.getCompany() != null) {
            BigDecimal turnover = party.getCompany().getAnnualTurnover();
            return turnover == null ? BigDecimal.ZERO : turnover.divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP);
        }
        if (party.getPerson() == null || party.getPerson().getDeclaredIncomes() == null) {
            return BigDecimal.ZERO;
        }
        return party.getPerson().getDeclaredIncomes().stream()
                .map(income -> AffordabilityMath.normaliseToMonthly(income.getAmount(), income.getFrequency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Income Clarity Assessment's employmentType input needs exactly one representative
     * Employment record. A party with no employments[] at all gets no Employment fact inserted
     * at all - the decision table has its own "no employment record" row (not Employment())
     * for that case, per Decision Workbook v0.3.
     */
    private Employment representativeEmployment(Party party) {
        if (party.getPerson() == null || party.getPerson().getEmployments() == null
                || party.getPerson().getEmployments().isEmpty()) {
            return null;
        }
        return party.getPerson().getEmployments().stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsCurrent()))
                .findFirst()
                .orElse(party.getPerson().getEmployments().get(0));
    }

    private void insertAll(KieSession session, Iterable<?> facts) {
        if (facts != null) {
            facts.forEach(session::insert);
        }
    }
}
