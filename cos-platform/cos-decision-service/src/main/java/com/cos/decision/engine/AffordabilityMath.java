package com.cos.decision.engine;

import com.cos.domain.enums.Frequency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Shared amortisation math for Income Clarity Assessment and Affordability, both of which need
 * "estimatedRepayment"/"requestedRepayment" = MonthlyInstalment(requestedAmount, termMonths,
 * policy interest rate) per the Decision Workbook. The Decision Workbook never states a concrete
 * policy interest rate, so a flat assumed annual rate is used here - see
 * {@link #ANNUAL_INTEREST_RATE}.
 */
final class AffordabilityMath {

    /** Assumed flat annual interest rate, since the Decision Workbook does not specify one. */
    static final BigDecimal ANNUAL_INTEREST_RATE = new BigDecimal("0.15");

    private static final int DEFAULT_TERM_MONTHS = 12;
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private AffordabilityMath() {
    }

    /** Standard amortising-loan monthly instalment formula: P * r / (1 - (1+r)^-n). */
    static BigDecimal monthlyInstalment(BigDecimal principal, Integer termMonths) {
        if (principal == null || principal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int n = (termMonths == null || termMonths <= 0) ? DEFAULT_TERM_MONTHS : termMonths;
        BigDecimal monthlyRate = ANNUAL_INTEREST_RATE.divide(BigDecimal.valueOf(12), MC);
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(n), MC);
        }
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal discountFactor = BigDecimal.ONE.subtract(
                BigDecimal.ONE.divide(onePlusR.pow(n, MC), MC));
        return principal.multiply(monthlyRate, MC).divide(discountFactor, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /** Normalises an amount received at the given frequency to an equivalent monthly amount. */
    static BigDecimal normaliseToMonthly(BigDecimal amount, Frequency frequency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (frequency == null) {
            return amount;
        }
        return switch (frequency) {
            case Weekly -> amount.multiply(BigDecimal.valueOf(52), MC).divide(BigDecimal.valueOf(12), MC);
            case Fortnightly -> amount.multiply(BigDecimal.valueOf(26), MC).divide(BigDecimal.valueOf(12), MC);
            case Monthly -> amount;
            case Annual -> amount.divide(BigDecimal.valueOf(12), MC);
        };
    }
}
