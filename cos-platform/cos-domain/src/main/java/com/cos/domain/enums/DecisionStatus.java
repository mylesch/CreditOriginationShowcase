package com.cos.domain.enums;

/**
 * The shared three-value outcome produced by most identity/credit/compliance/eligibility
 * decisions (Customer KYC/KYB Status, Customer PEPs Status, Customer Credit Status,
 * Party/Application Credit Eligibility, DecisionReason.proposedStatus).
 */
public enum DecisionStatus {
    DECLINE,
    REFER,
    APPROVE
}
