package com.cos.domain.enums;

/**
 * DecisionReason.checkType domain - a decision-reason-category list, distinct from (though
 * overlapping with) BureauCheckType. Kept as its own enum rather than unified with
 * BureauCheckType since the two value sets genuinely differ (e.g. Affordability, Document here;
 * ConsumerCredit/CommercialCredit split there).
 */
public enum DecisionCheckCategory {
    KYC,
    KYB,
    Credit,
    Compliance,
    BankValidation,
    Affordability,
    Document
}
