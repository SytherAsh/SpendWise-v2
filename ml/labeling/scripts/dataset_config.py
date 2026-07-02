"""
Registry of known raw datasets. Adding a new bank-statement export or SMS
capture only requires one new entry here — everything downstream (auto_label,
apply_review, merge_datasets) is generic and reads this config.

column_map: raw column name -> canonical field name (see categories.py).
            Map a raw column to None to drop it (not part of the canonical
            schema, e.g. a running Balance column).
source_type: "bank_statement" (already structured) or "sms_raw" (raw SMS
            body text — needs a sender-specific parser before it fits the
            canonical schema; see PARSER_REQUIRED below).
"""

DATASETS = {
    "spendwise_4yr_sbi_statement": {
        "raw_file": "raw/SpendWise_4yrs.xlsx",
        "source_type": "bank_statement",
        "source_label": "sms",  # canonical `source` field value written into every row
        "column_map": {
            "Transaction_Date": "transaction_date",
            "Debit": "debit",
            "Credit": "credit",
            "Balance": None,
            "Transaction_Mode": "transaction_mode",
            "DR/CR_Indicator": "dr_cr_indicator",
            "Transaction_ID": "transaction_id",
            "Recipient_Name": "recipient_name",
            "Bank": "bank",
            "UPI_ID": "upi_id",
            "Note": "note",
            "Amount": "amount",
        },
    },
    "captured_sms_2026": {
        "raw_file": "raw/captured_sms_cleaned.xlsx",
        "source_type": "sms_raw",
        "source_label": "sms",
        # 285 distinct sender IDs (many are DLT header variants of the same
        # bank, e.g. "JD-SBIUPI-S" / "AD-SBIUPI-S") and 666/1146 rows have no
        # sender at all. Auto-labeling needs structured fields first, so this
        # dataset is BLOCKED until it's run through a body parser. Reuse
        # android/app/src/main/kotlin/com/spendwise/parser/*.kt as the
        # reference implementation (same regex rules, same field names) —
        # do not hand-write a second, divergent parser here. See
        # ml/labeling/tracking/LABELING_STATUS.md for status.
        "column_map": None,
    },
}


def get_dataset(name: str) -> dict:
    if name not in DATASETS:
        raise KeyError(f"Unknown dataset '{name}'. Known datasets: {list(DATASETS)}")
    return DATASETS[name]
