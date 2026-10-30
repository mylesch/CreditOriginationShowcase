"""
Minimal, from-scratch generator for Drools decision-table .xlsx files, following the verified
layout (confirmed against a real working example downloaded from
fmarchioni/mastertheboss/drools/DecisionTable):

Row 1: RuleSet | <package>
Row 2: Import  | <comma-separated fully-qualified classes>
Row 4: RuleTable <name>                     (row 3 left blank as a separator)
Row 5: column-type markers: NAME | CONDITION | CONDITION | ... | ACTION | ... | PRIORITY | ACTIVATION-GROUP
Row 6: object-binding row: blank under NAME; "<var>: <Type>" under the FIRST condition column of a
       group of columns sharing one object; blank under continuation columns of the same object;
       blank under PRIORITY / ACTIVATION-GROUP (those columns use the data-row value directly).
Row 7: snippet/template row: the constraint expression (goes inside the bound object's parens),
       using $param for a single substituted value. Blank under PRIORITY / ACTIVATION-GROUP.
Row 8: caption row (human-readable column header, for documentation only).
Row 9+: data rows, one per rule. A blank cell under a CONDITION/ACTION column omits that
        constraint/action entirely for that rule (acts as "don't care").
"""
import openpyxl
from openpyxl.styles import Font

HEADER_FONT = Font(bold=True)


def write_table(path, package, imports, table_name, columns, rules, activation_group=None,
                start_priority=100):
    """
    columns: list of dicts, each with:
        kind: "name" | "condition" | "action" | "priority" | "activation_group"
        binding: str or None   (object-binding row content, e.g. "$br : BureauResponse"; None = blank)
        snippet: str or None   (constraint/action template, may contain $param; None = blank)
        caption: str           (human-readable header)
    rules: list of rows; each row is a list of cell values aligned 1:1 with `columns`
           (None/"" => blank => omit that condition/action for this rule)
    """
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "Sheet1"

    ws.cell(row=1, column=1, value="RuleSet")
    ws.cell(row=1, column=2, value=package)
    ws.cell(row=2, column=1, value="Import")
    ws.cell(row=2, column=2, value=", ".join(imports))

    ws.cell(row=4, column=1, value=f"RuleTable {table_name}")

    TYPE_ROW = 5
    BIND_ROW = 6
    SNIPPET_ROW = 7
    CAPTION_ROW = 8
    DATA_START = 9

    type_label = {
        "name": "NAME",
        "condition": "CONDITION",
        "action": "ACTION",
        "priority": "PRIORITY",
        "activation_group": "ACTIVATION-GROUP",
    }

    for ci, col in enumerate(columns):
        c = ci + 1
        ws.cell(row=TYPE_ROW, column=c, value=type_label[col["kind"]]).font = HEADER_FONT
        if col.get("binding"):
            ws.cell(row=BIND_ROW, column=c, value=col["binding"])
        if col.get("snippet"):
            ws.cell(row=SNIPPET_ROW, column=c, value=col["snippet"])
        ws.cell(row=CAPTION_ROW, column=c, value=col["caption"]).font = HEADER_FONT

    for ri, rule_row in enumerate(rules):
        r = DATA_START + ri
        for ci, col in enumerate(columns):
            c = ci + 1
            if col["kind"] == "priority":
                value = start_priority - ri
            elif col["kind"] == "activation_group":
                value = activation_group
            else:
                value = rule_row[ci] if ci < len(rule_row) else None
                if col["kind"] == "name" and value is not None:
                    # Drools rule names must be unique within a package, not just within a
                    # sheet - every decision table in this project shares one package, so the
                    # table name is always prefixed to guarantee no cross-table collisions
                    # (e.g. "Credit decline" appearing in both Party Credit Eligibility and
                    # Application Routing).
                    value = f"{table_name}: {value}"
            if value is not None and value != "":
                ws.cell(row=r, column=c, value=value)

    for c in range(1, len(columns) + 1):
        ws.column_dimensions[openpyxl.utils.get_column_letter(c)].width = 28

    wb.save(path)
    print("wrote", path)
