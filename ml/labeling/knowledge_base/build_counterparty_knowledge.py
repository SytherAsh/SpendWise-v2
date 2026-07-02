"""
Builds counterparty_knowledge.csv — a structured extraction of the "who/what is
this recipient" reasoning that came up constantly while building
merchant_rules.csv (the category knowledge base), but was never captured as
structured data, only as prose in tracking/LABELING_STATUS.md.

This is NOT a category knowledge base and NOT consumed by auto_label.py,
apply_review.py, merge_datasets.py, or ml/training/. It is a standalone
artifact for the future counterparty-metadata enrichment feature described in
docs/architecture.md "Future Enhancement" and ADR-010 in docs/decisions.md.
Nothing in the current application, database schema, or ML pipeline reads
this file. See counterparty_knowledge.README.md before using it for anything.

Usage:
    python build_counterparty_knowledge.py
"""

import csv
from pathlib import Path

KB_DIR = Path(__file__).parent
MERCHANT_RULES = KB_DIR / "merchant_rules.csv"
OUTPUT = KB_DIR / "counterparty_knowledge.csv"

# Individually confirmed during the interview (2026-07-02) — high confidence,
# specific relationship known, not just "this looks like a personal name."
FAMILY = {
    "PHONE_TRANSFER": "father (Samir Sawan; row Note field says \"Son\" -- money received from father)",
    "PRACHI S": "mother (money received from her)",
    "SAMEER B": "father (alternate account/alias)",
}
SELF = {
    "Mr Yash": "self-transfer between own accounts (Note field says \"self\")",
    "slice sm": "self-transfer -- Slice app, user's other account",
}
FRIEND = {
    "Mr Vihaa", "vihaansh", "AASHAY M", "SHUBHAM", "Pratham", "VINAYAK",
    "ALPHA VI", "Miss ALP", "alpharan", "SHLOK SA", "ADITI SH", "ASIM HEM",
    "PRANAV S", "PULKIT M", "KARTIK", "SOHAM KA", "SAKSHI S", "ANUSHKA",
    "NINAD SA", "MAYUR MA", "Mayur Ra", "Atharva", "ATHARV V", "Atharv V",
    "atharvah", "SNEHA DI", "SNEH BAGDI",
}

# Category -> counterparty type for everything that isn't Transfers. These are
# businesses/services, not people -- confidence is the same as the category
# knowledge base itself (each was individually confirmed or brand-recognized
# during labeling, see LABELING_STATUS.md).
CATEGORY_TO_TYPE = {
    "Food / Dine Out": "merchant",
    "Groceries": "merchant",
    "Shopping": "merchant",
    "Travel": "merchant",
    "Cosmetics": "merchant",
    "Entertainment": "merchant",
    "Subscriptions": "subscription_service",
    "Medical": "medical_provider",
    "Fees & Debt": "fee_or_debt_service",
    "Miscellaneous": "unknown",
}


def classify(name, category):
    if name in FAMILY:
        return "family", "high", FAMILY[name]
    if name in SELF:
        return "self", "high", SELF[name]
    if name in FRIEND:
        return "friend", "high", "named as a friend during the interview"
    if category == "Transfers":
        # Bulk-applied 2026-07-02: user delegated "any other names you find
        # are actually my friends" rather than confirming each individually.
        return "person_unconfirmed", "medium", (
            "personal-name-shaped recipient, bulk-classified as Transfers "
            "per user's delegated judgment call -- relationship (friend vs. "
            "family vs. unrecognized) was never individually confirmed"
        )
    ctype = CATEGORY_TO_TYPE.get(category, "unknown")
    return ctype, "medium", f"derived from category knowledge base ({category})"


def main():
    with open(MERCHANT_RULES, newline="", encoding="utf-8") as f:
        rules = list(csv.DictReader(f))

    out_rows = []
    for r in rules:
        ctype, confidence, notes = classify(r["pattern"], r["category"])
        out_rows.append({
            "recipient_pattern": r["pattern"],
            "match_field": r["match_field"],
            "counterparty_type": ctype,
            "confidence": confidence,
            "notes": notes,
            "derived_from_category": r["category"],
            "source_dataset": r["source_dataset"],
        })

    with open(OUTPUT, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "recipient_pattern", "match_field", "counterparty_type",
            "confidence", "notes", "derived_from_category", "source_dataset",
        ])
        writer.writeheader()
        writer.writerows(out_rows)

    from collections import Counter
    print(f"Wrote {OUTPUT}: {len(out_rows)} rows")
    print(Counter(r["counterparty_type"] for r in out_rows))
    print(Counter(r["confidence"] for r in out_rows))


if __name__ == "__main__":
    main()
