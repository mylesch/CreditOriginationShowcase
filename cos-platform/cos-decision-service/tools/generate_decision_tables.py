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

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                        "src", "main", "resources", "com", "cos", "decision", "rules", "generated")
OUT_DIR = os.path.abspath(OUT_DIR)
os.makedirs(OUT_DIR, exist_ok=True)

PACKAGE = "com.cos.decision.rules.generated"


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

print("Generated 5 decision tables into", OUT_DIR)
