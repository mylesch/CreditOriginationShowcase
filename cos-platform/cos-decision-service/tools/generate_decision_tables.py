"""
Generates the Drools decision-table .drl.xlsx resources under
cos-decision-service/src/main/resources/com/cos/decision/rules/generated/.

This is Phase 2's "first slice": five of the Decision Workbook v0.2's table-style, Party-scope
decisions (Customer PEPs Status, Bureau Score Band, Customer Credit Status, Bank Check Result,
Bank Account Validity). Rule rows below are transcribed 1:1 from COS Decision Workbook v0.2.
Re-run this script whenever the workbook's rule rows for these decisions change.

Run with: python3 generate_decision_tables.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_drools_table import write_table

RESOURCES = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                                          "src", "main", "resources"))

# Party-scope, Application-aggregate and Application-routing decisions are compiled into THREE
# separate KieBases (see kmodule.xml), each scoped to its own package via <kbase packages="...">.
#
# Party vs Application is split for the reason explained in PartyRuleEngine/kmodule.xml: with
# everything in one shared KieBase, inserting a plain Party fact into a Party-scope session (with
# Party.decisions still null) would still trigger Application-scope rules' "Party(decisions.
# creditEligibility == ...)" alpha-node constraints against it and NullPointerException, because
# Drools evaluates every loaded rule's pattern against every matching-type fact in any session
# built from that KieBase, not just the rules "intended" for that session.
#
# Application Routing is split out from Affordability/Credit Eligibility (App) for a sharper
# reason found by test: activation-group only cancels the OTHER activations that exist in its
# group AT THE MOMENT one of them fires. "Cancelled"/"External data required" become eligible
# immediately (before creditEligibility is even set), fire first, and set applicationRouting -
# but Credit Eligibility (App)'s own rules fire *afterwards* in that same session and call
# update() on the shared ApplicationDecisions fact, which creates BRAND NEW activations for
# "Credit approve"/"Credit refer"/etc in the ApplicationRouting group - activations that did not
# exist when "Cancelled" fired, so they are never cancelled, and one of them fires too,
# non-deterministically overwriting the routing "Cancelled" already decided. Running Application
# Routing in its own KieSession, only AFTER Affordability/Credit Eligibility (App) have already
# finished and their results are fixed in Java, removes the interleaving entirely.
PARTY_PACKAGE = "com.cos.decision.rules.party"
APP_PACKAGE = "com.cos.decision.rules.application"
ROUTING_PACKAGE = "com.cos.decision.rules.routing"
OUT_DIR_PARTY = os.path.join(RESOURCES, *PARTY_PACKAGE.split("."))
OUT_DIR_APP = os.path.join(RESOURCES, *APP_PACKAGE.split("."))
OUT_DIR_ROUTING = os.path.join(RESOURCES, *ROUTING_PACKAGE.split("."))
os.makedirs(OUT_DIR_PARTY, exist_ok=True)
os.makedirs(OUT_DIR_APP, exist_ok=True)
os.makedirs(OUT_DIR_ROUTING, exist_ok=True)

# Kept for the (now ten) Party-scope write_table() calls below, which still read `OUT_DIR` /
# `PACKAGE` unchanged - only the last three (Application-scope) calls use the _APP variants.
OUT_DIR = OUT_DIR_PARTY
PACKAGE = PARTY_PACKAGE


def band_in(*bands):
    """
    Raw CE text binding a SEPARATE $pd2 pattern constraining bureauScoreBand via a real pattern
    constraint (not eval()) - needed because eval() conditions are invisible to Drools' property
    reactivity analysis, so update($pd) from an earlier rule (e.g. Bureau Score Band) would never
    re-trigger a rule whose only dependency on that field is hidden inside an eval(). $pd2 is a
    second, independently-matched binding of the same PartyDecisions type/fact - there is only
    ever one PartyDecisions fact in a party-scoped session, so $pd and $pd2 always refer to the
    same instance; only the binding name differs.
    """
    values = ", ".join(f"com.cos.domain.enums.BureauScoreBand.{b}" for b in bands)
    return f"$pd2 : PartyDecisions(bureauScoreBand in ({values}))"


LIABILITY_ARREARS_EXISTS = ("exists Liability(arrears != null, monthlyRepayment != null, "
                             "arrears > monthlyRepayment)")


def rebind(var, type_name, constraint):
    """
    Raw CE text for a fresh, independently-bound pattern of `type_name` with `constraint` inside
    its parens - the standard way (see band_in() above) to read a field set by an earlier rule in
    the same session as a REAL pattern constraint rather than an eval(), so it is visible to
    Drools' property-reactivity tracking and the dependent rule re-activates correctly.
    """
    return f"{var} : {type_name}({constraint})"

# ===========================================================================
# 1. Customer PEPs Status
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "CustomerPepsStatus.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.BureauResponse", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.BureauCheckType", "com.cos.domain.enums.DecisionStatus"],
    table_name="CustomerPepsStatus",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$br : BureauResponse",
         "snippet": "checkType == BureauCheckType.Compliance, isPEPsMatch == $param",
         "caption": "isPEPsMatch"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$pd.setCustomerPEPsStatus(DecisionStatus.$param); update($pd);",
         "caption": "customerPEPsStatus"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["No PEPs match", "false", "Y", "APPROVE"],
        ["PEPs match", "true", "Y", "REFER"],
    ],
    activation_group="CustomerPepsStatus",
)

# ===========================================================================
# 2. Bureau Score Band
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "BureauScoreBand.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.BureauResponse", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.BureauScoreBand"],
    table_name="BureauScoreBand",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$br : BureauResponse", "snippet": "numericBureauScore $param",
         "caption": "numericBureauScore (lower bound)"},
        {"kind": "condition", "binding": None,
         "snippet": "eval($br.getNumericBureauScore().doubleValue() $param)",
         "caption": "numericBureauScore (upper bound)"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$pd.setBureauScoreBand(BureauScoreBand.$param); update($pd);",
         "caption": "bureauScoreBand"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["BAND_A", ">= 750", None, "Y", "BAND_A"],
        ["BAND_B", ">= 650", "< 750", "Y", "BAND_B"],
        ["BAND_C", ">= 550", "< 650", "Y", "BAND_C"],
        ["BAND_D", ">= 450", "< 550", "Y", "BAND_D"],
        ["BAND_E", "< 450", None, "Y", "BAND_E"],
    ],
    activation_group="BureauScoreBand",
)

# ===========================================================================
# 3. Customer Credit Status
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "CustomerCreditStatus.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.Liability", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.DecisionStatus"],
    table_name="CustomerCreditStatus",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "hasSeriousArrears"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "bureauScoreBand"},
        {"kind": "action", "snippet": "$pd.setCustomerCreditStatus(DecisionStatus.$param); update($pd);",
         "caption": "customerCreditStatus"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["Serious arrears", "Y", LIABILITY_ARREARS_EXISTS, None, "DECLINE"],
        ["Band A or B, clean", "Y", None, band_in("BAND_A", "BAND_B"), "APPROVE"],
        ["Band C, clean", "Y", None, band_in("BAND_C"), "REFER"],
        ["Band D or E", "Y", None, band_in("BAND_D", "BAND_E"), "DECLINE"],
    ],
    activation_group="CustomerCreditStatus",
)

# ===========================================================================
# 4. Bank Check Result
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "BankCheckResult.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.BureauResponse", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.BureauCheckType", "com.cos.domain.enums.BankCheckResultCode",
             "com.cos.domain.enums.BankCheckResult"],
    table_name="BankCheckResult",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$br : BureauResponse",
         "snippet": "checkType == BureauCheckType.BankCheck, bankCheckResult == BankCheckResultCode.$param",
         "caption": "bankCheckResult code"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$pd.setBankCheckResult(BankCheckResult.$param); update($pd);",
         "caption": "bankCheckResult"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["BC01 Full match", "BC01", "Y", "MATCH"],
        ["BC02 Partial name match", "BC02", "Y", "NO_MATCH"],
        ["BC03 Account closed", "BC03", "Y", "NO_MATCH"],
        ["BC04 Account not found", "BC04", "Y", "NO_MATCH"],
        ["BC05 Owner mismatch", "BC05", "Y", "NO_MATCH"],
        ["BC06 Pending at bureau", "BC06", "Y", "PENDING"],
    ],
    activation_group="BankCheckResult",
)

# ===========================================================================
# 5. Bank Account Validity
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "BankAccountValidity.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.Application", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.ManualBankDecision", "com.cos.domain.enums.BankAccountValidity"],
    table_name="BankAccountValidity",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$app : Application", "snippet": "manualBankDecision == ManualBankDecision.$param",
         "caption": "manualBankDecision"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "condition", "binding": "$pd2 : PartyDecisions",
         "snippet": "bankCheckResult == BankCheckResult.$param",
         "caption": "bankCheckResult"},
        {"kind": "action", "snippet": "$pd.setBankAccountValidity(BankAccountValidity.$param); update($pd);",
         "caption": "bankAccountValidity"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["Manual approve", "APPROVE", "Y", None, "APPROVE"],
        ["Manual decline", "DECLINE", "Y", None, "REFER"],
        ["No override, match", "NONE", "Y", "MATCH", "APPROVE"],
        ["No override, no match", "NONE", "Y", "NO_MATCH", "REFER"],
        ["No override, pending", "NONE", "Y", "PENDING", "REFER"],
    ],
    activation_group="BankAccountValidity",
)

# ===========================================================================
# 6. Credit Status Override
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "CreditStatusOverride.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.Application", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.ManualCreditDecision", "com.cos.domain.enums.CreditStatusOverrideValue"],
    table_name="CreditStatusOverride",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$app : Application",
         "snippet": "manualCreditDecision == ManualCreditDecision.$param", "caption": "manualCreditDecision"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$pd.setCreditStatusOverride(CreditStatusOverrideValue.$param); update($pd);",
         "caption": "creditStatusOverride"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["Approve", "APPROVE", "Y", "APPROVE"],
        ["Decline", "DECLINE", "Y", "DECLINE"],
        ["Refer", "REFER", "Y", "REFER"],
        ["None", "NONE", "Y", "NONE"],
    ],
    activation_group="CreditStatusOverride",
)

# ===========================================================================
# 7. Customer KYC Status
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "CustomerKycStatus.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.BureauResponse", "com.cos.domain.model.Identification",
             "com.cos.domain.model.PartyDecisions", "com.cos.domain.enums.BureauCheckType",
             "com.cos.domain.enums.DecisionStatus"],
    table_name="CustomerKycStatus",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$br : BureauResponse",
         "snippet": "checkType == BureauCheckType.KYC, isIdentityMatch == $param", "caption": "isIdentityMatch"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "identityMatchConfidence (lower)"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "identityMatchConfidence (upper)"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "isIdentificationExpired"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$pd.setCustomerKYCStatus(DecisionStatus.$param); update($pd);",
         "caption": "customerKYCStatus"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["No identity match", "false", None, None, None, "Y", "DECLINE"],
        # isIdentityMatch is pinned to "true" here (rather than wildcarded, as the Decision
        # Workbook's own "-" shows it) because "No identity match" above already has higher
        # priority and catches every isIdentityMatch=false case first - the net outcome is
        # identical, since a false-match + expired identification still DECLINEs via row 1.
        ["Identification expired", "true", None, None,
         rebind("$id", "Identification", "expiresOn != null, expiresOn < java.time.LocalDate.now()"), "Y", "REFER"],
        ["High confidence match", "true",
         rebind("$br2", "BureauResponse", "checkType == BureauCheckType.KYC, identityMatchConfidence >= 95"),
         None, None, "Y", "APPROVE"],
        ["Medium confidence match", "true",
         rebind("$br2", "BureauResponse", "checkType == BureauCheckType.KYC, identityMatchConfidence >= 80"),
         rebind("$br3", "BureauResponse", "checkType == BureauCheckType.KYC, identityMatchConfidence < 95"),
         None, "Y", "REFER"],
        ["Low confidence match", "true", None,
         rebind("$br3", "BureauResponse", "checkType == BureauCheckType.KYC, identityMatchConfidence < 80"),
         None, "Y", "DECLINE"],
    ],
    activation_group="CustomerKycStatus",
)
# Note: rule 2 ("No identity match") uses isIdentityMatch="false" so the "-" wildcard in column 1
# for rule 2 above is only a label placeholder; see the actual data cell used (None marks wildcard
# other than column 1, which is always explicit true/false per row as required by binding $br).

def reg_status(status):
    return rebind("$br4", "BureauResponse",
                  f"checkType == BureauCheckType.KYB, companyRegistrationStatus == CompanyRegistrationStatus.{status}")


def confidence_band(var, lower, upper):
    parts = []
    if lower is not None:
        parts.append(f"identityMatchConfidence >= {lower}")
    if upper is not None:
        parts.append(f"identityMatchConfidence < {upper}")
    return rebind(var, "BureauResponse", "checkType == BureauCheckType.KYB, " + ", ".join(parts))


# ===========================================================================
# 8. Customer KYB Status
# ===========================================================================
write_table(
    os.path.join(OUT_DIR, "CustomerKybStatus.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.BureauResponse", "com.cos.domain.model.PartyDecisions",
             "com.cos.domain.enums.BureauCheckType", "com.cos.domain.enums.CompanyRegistrationStatus",
             "com.cos.domain.enums.DecisionStatus"],
    table_name="CustomerKybStatus",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$br : BureauResponse",
         "snippet": "checkType == BureauCheckType.KYB, isIdentityMatch == $param", "caption": "isIdentityMatch"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "companyRegistrationStatus"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "identityMatchConfidence"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "hasRepresentativeAuthority"},
        {"kind": "condition", "binding": "$pd : PartyDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$pd.setCustomerKYBStatus(DecisionStatus.$param); update($pd);",
         "caption": "customerKYBStatus"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["No company match", "false", None, None, None, "Y", "DECLINE"],
        # As with Customer KYC Status, isIdentityMatch is pinned "true" below rather than
        # wildcarded: "No company match" above already has higher priority and catches every
        # isIdentityMatch=false case first, so the net outcome is unchanged.
        ["Company deregistered", "true", reg_status("Deregistered"), None, None, "Y", "DECLINE"],
        ["Company in liquidation", "true", reg_status("InLiquidation"), None, None, "Y", "DECLINE"],
        ["Registration status unknown", "true", reg_status("Unknown"), None, None, "Y", "REFER"],
        ["Representative authority not confirmed", "true", reg_status("Active"), None,
         rebind("$pd2", "PartyDecisions", "hasRepresentativeAuthority == false"), "Y", "REFER"],
        ["High confidence, authority confirmed", "true", reg_status("Active"),
         confidence_band("$br3", 95, None),
         rebind("$pd2", "PartyDecisions", "hasRepresentativeAuthority == true"), "Y", "APPROVE"],
        ["Medium confidence, authority confirmed", "true", reg_status("Active"),
         confidence_band("$br3", 80, 95),
         rebind("$pd2", "PartyDecisions", "hasRepresentativeAuthority == true"), "Y", "REFER"],
        ["Low confidence, authority confirmed", "true", reg_status("Active"),
         confidence_band("$br3", None, 80),
         rebind("$pd2", "PartyDecisions", "hasRepresentativeAuthority == true"), "Y", "DECLINE"],
    ],
    activation_group="CustomerKybStatus",
)

def employment_in(*types):
    values = ", ".join(f"com.cos.domain.enums.EmploymentType.{t}" for t in types)
    return rebind("$emp", "Employment", f"employmentType in ({values})")


def ratio_bound(var, lower, upper):
    parts = []
    if lower is not None:
        parts.append(f"incomeToRepaymentRatio >= {lower}")
    if upper is not None:
        parts.append(f"incomeToRepaymentRatio < {upper}")
    return rebind(var, "IncomeClarityAssessment", ", ".join(parts))


# ===========================================================================
# 9. Income Clarity Assessment
# ===========================================================================
# declaredIncome / estimatedRepayment / incomeToRepaymentRatio are computed in Java
# (PartyRuleEngine) and set on the IncomeClarityAssessment fact before it is inserted; this
# table only resolves isIncomeClarityRequired from partyType + employmentType + that ratio.
write_table(
    os.path.join(OUT_DIR, "IncomeClarityAssessment.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.Party", "com.cos.domain.model.Employment",
             "com.cos.domain.model.IncomeClarityAssessment", "com.cos.domain.enums.PartyType"],
    table_name="IncomeClarityAssessment",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$party : Party", "snippet": "partyType == PartyType.$param",
         "caption": "partyType"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "employmentType"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "incomeToRepaymentRatio (lower)"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "incomeToRepaymentRatio (upper)"},
        {"kind": "condition", "binding": "$ica : IncomeClarityAssessment", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$ica.setIsIncomeClarityRequired($param); update($ica);",
         "caption": "isIncomeClarityRequired"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["Business income needs documents", "Business", None, None, None, "Y", "true"],
        ["Variable/self-employed income", "Consumer", employment_in("SelfEmployed", "Contract"), None, None, "Y", "true"],
        ["Clear ratio", "Consumer", employment_in("FullTime", "PartTime"), ratio_bound("$ica2", 5, None), None, "Y", "false"],
        ["Marginal ratio", "Consumer", employment_in("FullTime", "PartTime"), ratio_bound("$ica2", 3, None),
         ratio_bound("$ica3", None, 5), "Y", "false"],
        ["Thin ratio", "Consumer", employment_in("FullTime", "PartTime"), None, ratio_bound("$ica3", None, 3), "Y", "true"],
        ["Low-confidence employment type", "Consumer",
         employment_in("Retired", "Unemployed", "Student"), None, None, "Y", "true"],
        ["No employment record at all", "Consumer", "not Employment()", None, None, "Y", "true"],
    ],
    activation_group="IncomeClarityAssessment",
)

def identity_status(party_type_field, value):
    """identityStatus* = customerKYCStatus for Consumer, customerKYBStatus for Business."""
    field = "customerKYCStatus" if party_type_field == "Consumer" else "customerKYBStatus"
    return rebind("$pd2", "PartyDecisions", f"{field} == DecisionStatus.{value}")


def credit_status(value):
    return rebind("$pd3", "PartyDecisions", f"customerCreditStatus == DecisionStatus.{value}")


def peps_status(value):
    return rebind("$pd4", "PartyDecisions", f"customerPEPsStatus == DecisionStatus.{value}")


# ===========================================================================
# 10. Party Credit Eligibility
# ===========================================================================
# identityStatus* (per the Decision Workbook) is customerKYCStatus for a Consumer/linked
# individual and customerKYBStatus for a Business party - the three rows that reference it
# (identityStatus=DECLINE/REFER, and the all-APPROVE row) are each split into a Consumer and a
# Business variant below, rather than adding a new "identityStatus" field to the domain model.
write_table(
    os.path.join(OUT_DIR, "PartyCreditEligibility.drl.xlsx"),
    package=PACKAGE,
    imports=["com.cos.domain.model.PartyDecisions", "com.cos.domain.enums.CreditStatusOverrideValue",
             "com.cos.domain.enums.DecisionStatus"],
    table_name="PartyCreditEligibility",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$pd : PartyDecisions",
         "snippet": "creditStatusOverride == CreditStatusOverrideValue.$param", "caption": "creditStatusOverride"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "identityStatus"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "customerCreditStatus"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "customerPEPsStatus"},
        {"kind": "action", "snippet": "$pd.setCreditEligibility(DecisionStatus.$param); update($pd);",
         "caption": "creditEligibility"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["Override approve", "APPROVE", None, None, None, "APPROVE"],
        ["Override decline", "DECLINE", None, None, None, "DECLINE"],
        ["Override refer", "REFER", None, None, None, "REFER"],
        ["Identity decline (Consumer)", "NONE", identity_status("Consumer", "DECLINE"), None, None, "DECLINE"],
        ["Identity decline (Business)", "NONE", identity_status("Business", "DECLINE"), None, None, "DECLINE"],
        ["Credit decline", "NONE", None, credit_status("DECLINE"), None, "DECLINE"],
        ["PEPs decline", "NONE", None, None, peps_status("DECLINE"), "DECLINE"],
        ["Identity refer (Consumer)", "NONE", identity_status("Consumer", "REFER"), None, None, "REFER"],
        ["Identity refer (Business)", "NONE", identity_status("Business", "REFER"), None, None, "REFER"],
        ["Credit refer", "NONE", None, credit_status("REFER"), None, "REFER"],
        ["PEPs refer", "NONE", None, None, peps_status("REFER"), "REFER"],
        ["All approve (Consumer)", "NONE", identity_status("Consumer", "APPROVE"),
         credit_status("APPROVE"), peps_status("APPROVE"), "APPROVE"],
        ["All approve (Business)", "NONE", identity_status("Business", "APPROVE"),
         credit_status("APPROVE"), peps_status("APPROVE"), "APPROVE"],
    ],
    activation_group="PartyCreditEligibility",
)

def affordability_status(value):
    return rebind("$aff2", "Affordability", f"affordabilityStatus == AffordabilityStatus.{value}")


def affordability_status_in(*values):
    values_text = ", ".join(f"AffordabilityStatus.{v}" for v in values)
    return rebind("$aff2", "Affordability", f"affordabilityStatus in ({values_text})")


def party_eligibility_exists(value):
    return f"exists Party(decisions != null, decisions.creditEligibility == DecisionStatus.{value})"


def party_eligibility_none_other_than(value):
    # A party with no decisions at all must disqualify "every party approved" too, not just be
    # skipped - treated as "decisions == null OR creditEligibility != value" in one expression
    # (not as two comma-separated constraints, which would AND rather than OR them).
    return f"not Party(decisions == null || decisions.creditEligibility != DecisionStatus.{value})"


# ===========================================================================
# 11. Affordability
# ===========================================================================
# totalExpenses / totalLiabilityRepayments / requestedRepayment / disposableIncome are computed
# in Java (ApplicationRuleEngine) and set on the Affordability fact before it is inserted; this
# table only resolves affordabilityStatus from incomeBase + the disposableIncome ratio.
write_table(
    os.path.join(OUT_DIR_APP, "Affordability.drl.xlsx"),
    package=APP_PACKAGE,
    imports=["com.cos.domain.model.NetIncome", "com.cos.domain.model.Affordability",
             "com.cos.domain.enums.IncomeBase", "com.cos.domain.enums.AffordabilityStatus"],
    table_name="Affordability",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "incomeBase"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "ratio (lower)"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "ratio (upper)"},
        {"kind": "condition", "binding": "$aff : Affordability", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$aff.setAffordabilityStatus(AffordabilityStatus.$param); update($aff);",
         "caption": "affordabilityStatus"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["Income clarity insufficient", rebind("$ni", "NetIncome", "incomeBase == IncomeBase.MISSING"),
         None, None, "Y", "REQUIRES_DOCUMENTS"],
        ["Comfortable margin", None,
         rebind("$aff2a", "Affordability", "disposableIncome != null, requestedRepayment != null, "
                "disposableIncome.doubleValue() >= requestedRepayment.doubleValue() * 1.25"),
         None, "Y", "PASS"],
        ["Narrow margin", None,
         rebind("$aff2b", "Affordability", "disposableIncome != null, requestedRepayment != null, "
                "disposableIncome.doubleValue() >= requestedRepayment.doubleValue(), "
                "disposableIncome.doubleValue() < requestedRepayment.doubleValue() * 1.25"),
         None, "Y", "REFER"],
        ["Insufficient margin", None,
         None,
         rebind("$aff2c", "Affordability", "disposableIncome != null, requestedRepayment != null, "
                "disposableIncome.doubleValue() < requestedRepayment.doubleValue()"),
         "Y", "FAIL"],
    ],
    activation_group="Affordability",
)

# ===========================================================================
# 12. Credit Eligibility (App)
# ===========================================================================
write_table(
    os.path.join(OUT_DIR_APP, "CreditEligibilityApp.drl.xlsx"),
    package=APP_PACKAGE,
    imports=["com.cos.domain.model.Party", "com.cos.domain.model.ApplicationDecisions",
             "com.cos.domain.model.Affordability", "com.cos.domain.enums.DecisionStatus",
             "com.cos.domain.enums.AffordabilityStatus"],
    table_name="CreditEligibilityApp",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "party eligibility"},
        {"kind": "condition", "binding": "$ad : ApplicationDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "affordabilityStatus"},
        {"kind": "action", "snippet": "$ad.setCreditEligibility(DecisionStatus.$param); update($ad);",
         "caption": "creditEligibility"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        ["At least one party declined", party_eligibility_exists("DECLINE"), "Y", None, "DECLINE"],
        ["Affordability failed", None, "Y", affordability_status("FAIL"), "DECLINE"],
        ["Affordability requires documents", None, "Y", affordability_status("REQUIRES_DOCUMENTS"), "REFER"],
        ["At least one party referred", party_eligibility_exists("REFER"), "Y",
         affordability_status_in("PASS", "REFER"), "REFER"],
        ["Every party approved, affordable", party_eligibility_none_other_than("APPROVE"), "Y",
         affordability_status("PASS"), "APPROVE"],
        ["Every party approved, affordability marginal", party_eligibility_none_other_than("APPROVE"), "Y",
         affordability_status("REFER"), "REFER"],
    ],
    activation_group="CreditEligibilityApp",
)


def request_type_in(*types):
    values = ", ".join(f"RequestType.{t}" for t in types)
    return rebind("$app3", "Application", f"requestType in ({values})")


def bureau_required():
    return rebind("$ad2", "ApplicationDecisions", "externalDataRequired.isBureauRequired == true")


def docs_required():
    # ApplicationDecisions has no separate areDocumentsRequired field - it's requiredDocuments[]
    # (a plain List<RequiredDocument>) being non-empty, per the Decision Workbook's own notes.
    return rebind("$ad3", "ApplicationDecisions", "requiredDocuments != null, requiredDocuments.size() > 0")


def app_credit_eligibility_in(*values):
    values_text = ", ".join(f"DecisionStatus.{v}" for v in values)
    return rebind("$ad4", "ApplicationDecisions", f"creditEligibility in ({values_text})")


def app_credit_eligibility(value):
    return rebind("$ad4", "ApplicationDecisions", f"creditEligibility == DecisionStatus.{value}")


def primary_bank_validity(value):
    return rebind("$primary", "Party",
                  f"isPrimary == true, decisions != null, decisions.bankAccountValidity == BankAccountValidity.{value}")


# ===========================================================================
# 13. Application Routing
# ===========================================================================
write_table(
    os.path.join(OUT_DIR_ROUTING, "ApplicationRouting.drl.xlsx"),
    package=ROUTING_PACKAGE,
    imports=["com.cos.domain.model.Application", "com.cos.domain.model.ApplicationDecisions",
             "com.cos.domain.model.Party", "com.cos.domain.enums.RequestType",
             "com.cos.domain.enums.DecisionStatus", "com.cos.domain.enums.BankAccountValidity",
             "com.cos.domain.enums.ApplicationRouting"],
    table_name="ApplicationRouting",
    columns=[
        {"kind": "name", "caption": "Rule Name"},
        {"kind": "condition", "binding": "$app : Application", "snippet": None, "caption": "bind application"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "isCancelled"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "isBureauRequired"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "requestType"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "areDocumentsRequired"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "creditEligibility"},
        {"kind": "condition", "binding": None, "snippet": "$param", "caption": "bankAccountValidity"},
        {"kind": "condition", "binding": "$ad : ApplicationDecisions", "snippet": None, "caption": "bind result"},
        {"kind": "action", "snippet": "$ad.setApplicationRouting(ApplicationRouting.$param); update($ad);",
         "caption": "applicationRouting"},
        {"kind": "priority", "caption": "Priority"},
        {"kind": "activation_group", "caption": "Activation group"},
    ],
    rules=[
        # [name, $app-bind, isCancelled, isBureauRequired, requestType, areDocumentsRequired,
        #  creditEligibility, bankAccountValidity, $ad-bind, applicationRouting]
        ["Cancelled", "Y", rebind("$app2", "Application", "isCancelled == true"),
         None, None, None, None, None, "Y", "CANCEL"],
        ["External data required", "Y", None, bureau_required(), None, None, None, None, "Y", "EXTERNAL_DATA_REQUIRED"],
        ["Credit decline", "Y", None, None, request_type_in("CREDIT_CHECK"), None,
         app_credit_eligibility("DECLINE"), None, "Y", "CREDIT_DECLINE"],
        ["Credit - get documents", "Y", None, None, request_type_in("CREDIT_CHECK"), docs_required(),
         app_credit_eligibility_in("REFER", "APPROVE"), None, "Y", "GET_DOCUMENTS"],
        ["Credit refer", "Y", None, None, request_type_in("CREDIT_CHECK"), None,
         app_credit_eligibility("REFER"), None, "Y", "CREDIT_REFER"],
        ["Credit approve", "Y", None, None, request_type_in("CREDIT_CHECK"), None,
         app_credit_eligibility("APPROVE"), None, "Y", "CREDIT_APPROVE"],
        ["Bank refer", "Y", None, None, request_type_in("BANK_CHECK"), None, None,
         primary_bank_validity("REFER"), "Y", "BANK_REFER"],
        ["Bank approve", "Y", None, None, request_type_in("BANK_CHECK"), None,
         app_credit_eligibility("APPROVE"), primary_bank_validity("APPROVE"), "Y", "BANK_APPROVE"],
        ["Full origination - get documents", "Y", None, None, request_type_in("FULL_ORIGINATION"), docs_required(),
         app_credit_eligibility_in("REFER", "APPROVE"), None, "Y", "GET_DOCUMENTS"],
        ["Full origination - credit decline", "Y", None, None, request_type_in("FULL_ORIGINATION"), None,
         app_credit_eligibility("DECLINE"), None, "Y", "CREDIT_DECLINE"],
        ["Full origination - bank refer", "Y", None, None, request_type_in("FULL_ORIGINATION"), None,
         app_credit_eligibility_in("REFER", "APPROVE"), primary_bank_validity("REFER"), "Y", "BANK_REFER"],
        ["Full origination - credit refer", "Y", None, None, request_type_in("FULL_ORIGINATION"), None,
         app_credit_eligibility("REFER"), primary_bank_validity("APPROVE"), "Y", "CREDIT_REFER"],
        ["Full origination - bank approve", "Y", None, None, request_type_in("FULL_ORIGINATION"), None,
         app_credit_eligibility("APPROVE"), primary_bank_validity("APPROVE"), "Y", "BANK_APPROVE"],
    ],
    activation_group="ApplicationRouting",
)

print("Generated Party-scope decision tables into", OUT_DIR_PARTY)
print("Generated Application-aggregate decision tables into", OUT_DIR_APP)
print("Generated Application-routing decision table into", OUT_DIR_ROUTING)
