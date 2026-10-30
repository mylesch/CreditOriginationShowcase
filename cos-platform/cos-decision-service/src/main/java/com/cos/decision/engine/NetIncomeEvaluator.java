package com.cos.decision.engine;

import com.cos.domain.enums.IncomeBase;
import com.cos.domain.model.Application;
import com.cos.domain.model.DocumentExtraction;
import com.cos.domain.model.NetIncome;
import com.cos.domain.model.Party;
import com.cos.domain.model.PartyDecisions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Net Income (Decision Workbook v0.2) - ruleset-style, hand-written Java.
 *
 * <p>confirmedIncome (a back-office reviewer's confirmed figure) has no home in the BOM
 * (the Decision Workbook refers to "Application.caseData", which is not an actual field) - it is
 * always treated as absent here, so this evaluator's precedence chain only ever resolves to
 * DETECTED, DECLARED or MISSING, never CONFIRMED.
 *
 * <p>"declaredIncome is present and PartyDecisions.incomeClarity.isIncomeClarityRequired = false"
 * is read against the primary party (isPrimary=true), since the Decision Workbook refers to a
 * single PartyDecisions, not an aggregate across every party.
 */
@Component
public class NetIncomeEvaluator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    public NetIncome evaluate(Application application, List<DocumentExtraction> extractions,
                               com.cos.domain.model.DocumentReviewStatus documentReviewStatus) {
        BigDecimal declaredIncome = sumDeclaredIncome(application);
        BigDecimal detectedIncome = detectedIncome(extractions, documentReviewStatus);

        BigDecimal totalIncome;
        IncomeBase incomeBase;
        if (detectedIncome != null) {
            totalIncome = detectedIncome;
            incomeBase = IncomeBase.DETECTED;
        } else if (declaredIncome.signum() > 0 && isIncomeClaritySatisfied(application)) {
            totalIncome = declaredIncome;
            incomeBase = IncomeBase.DECLARED;
        } else {
            totalIncome = BigDecimal.ZERO;
            incomeBase = IncomeBase.MISSING;
        }

        return NetIncome.builder()
                .declaredIncome(declaredIncome)
                .detectedIncome(detectedIncome)
                .totalIncome(totalIncome)
                .incomeBase(incomeBase)
                .build();
    }

    private BigDecimal sumDeclaredIncome(Application application) {
        if (application.getParties() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Party party : application.getParties()) {
            if (party.getCompany() != null && party.getCompany().getAnnualTurnover() != null) {
                total = total.add(party.getCompany().getAnnualTurnover().divide(BigDecimal.valueOf(12), MC));
            } else if (party.getPerson() != null && party.getPerson().getDeclaredIncomes() != null) {
                for (var income : party.getPerson().getDeclaredIncomes()) {
                    total = total.add(AffordabilityMath.normaliseToMonthly(income.getAmount(), income.getFrequency()));
                }
            }
        }
        return total;
    }

    private boolean isIncomeClaritySatisfied(Application application) {
        if (application.getParties() == null) {
            return false;
        }
        return application.getParties().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsPrimary()))
                .map(Party::getDecisions)
                .filter(d -> d != null && d.getIncomeClarity() != null)
                .map(PartyDecisions::getIncomeClarity)
                .anyMatch(ica -> Boolean.FALSE.equals(ica.getIsIncomeClarityRequired()));
    }

    /** Average extractedIncomeAmount across verified income documents, excluding outliers &gt;2 std-dev from the mean. */
    private BigDecimal detectedIncome(List<DocumentExtraction> extractions, com.cos.domain.model.DocumentReviewStatus status) {
        if (extractions == null || extractions.isEmpty() || status == null
                || !Boolean.TRUE.equals(status.getIsAutoVerificationComplete())) {
            return null;
        }
        List<BigDecimal> amounts = extractions.stream()
                .map(DocumentExtraction::getExtractedIncomeAmount)
                .filter(amount -> amount != null)
                .toList();
        if (amounts.isEmpty()) {
            return null;
        }
        BigDecimal mean = average(amounts);
        BigDecimal stdDev = standardDeviation(amounts, mean);
        BigDecimal lower = mean.subtract(stdDev.multiply(BigDecimal.valueOf(2)));
        BigDecimal upper = mean.add(stdDev.multiply(BigDecimal.valueOf(2)));

        List<BigDecimal> withoutOutliers = amounts.stream()
                .filter(a -> a.compareTo(lower) >= 0 && a.compareTo(upper) <= 0)
                .toList();
        return withoutOutliers.isEmpty() ? mean : average(withoutOutliers);
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    private BigDecimal standardDeviation(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal sumSquares = values.stream()
                .map(v -> v.subtract(mean).pow(2, MC))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = sumSquares.divide(BigDecimal.valueOf(values.size()), MC);
        return variance.sqrt(MC);
    }
}
