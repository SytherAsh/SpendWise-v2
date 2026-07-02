"""
Step 3. Concatenates every datasets/<name>/labeled.csv into
ml/data/spendwise_labeled.xlsx — the single file ml/training/train.py reads.
Drops duplicate transaction_ids across datasets (the same real transaction
can show up in more than one source, e.g. a bank export and a later SMS
capture of the same period).

Some raw bank-statement exports reuse a placeholder transaction_id (e.g.
'0002564') across genuinely distinct transactions instead of leaving it
blank — seen in spendwise_4yr_sbi_statement, where the same placeholder ID
was reused across 5 different monthly stipend deposits. Deduping on that
raw ID would silently drop real transactions. Before deduping, any
transaction_id that is missing or shared by rows with different
(recipient_name/upi_id, amount, transaction_date) content gets replaced
with a synthesized ID, using the same formula docs/database.md already
defines for SMS with no bank-provided reference number: sha256(recipient_or_upi
|| amount || date). Genuinely duplicate rows (identical content) still hash
identically and get deduped as intended.

Usage:
    python merge_datasets.py
"""

import hashlib
import sys
from pathlib import Path

import pandas as pd

LABELING_ROOT = Path(__file__).parent.parent
DATA_DIR = LABELING_ROOT.parent / "data"
OUTPUT = DATA_DIR / "spendwise_labeled.xlsx"


def synthesize_transaction_id(row):
    identity = row.get("upi_id") or row.get("recipient_name") or ""
    raw = f"{identity}|{row.get('amount')}|{row.get('transaction_date')}"
    return "synth_" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def fix_colliding_ids(df):
    """Replace transaction_id for rows that are missing one, or whose ID is
    shared by rows with different underlying transaction content."""
    content_key = df["upi_id"].fillna("") + "|" + df["recipient_name"].fillna("") + \
        "|" + df["amount"].astype(str) + "|" + df["transaction_date"].astype(str)
    id_is_missing = df["transaction_id"].isna() | (df["transaction_id"] == "")
    df = df.assign(_content_key=content_key)
    distinct_content_per_id = df.groupby("transaction_id")["_content_key"].transform("nunique")
    id_collides = distinct_content_per_id > 1

    needs_fix = id_is_missing | id_collides
    fixed = df.loc[needs_fix].apply(synthesize_transaction_id, axis=1)
    df.loc[needs_fix, "transaction_id"] = fixed
    return df.drop(columns="_content_key"), int(needs_fix.sum())


def main():
    labeled_files = sorted(LABELING_ROOT.glob("datasets/*/labeled.csv"))
    if not labeled_files:
        print("No labeled.csv files found under ml/labeling/datasets/*/. "
              "Run auto_label.py + apply_review.py for at least one dataset first.")
        sys.exit(1)

    frames = []
    for path in labeled_files:
        df = pd.read_csv(path)
        df["_source_dataset"] = path.parent.name
        frames.append(df)

    combined = pd.concat(frames, ignore_index=True)
    combined, num_synthesized = fix_colliding_ids(combined)
    if num_synthesized:
        print(f"Synthesized {num_synthesized} transaction_id(s) for rows with missing/colliding source IDs.")

    before = len(combined)
    combined = combined.drop_duplicates(subset="transaction_id", keep="first")
    deduped = before - len(combined)

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    combined.to_excel(OUTPUT, index=False)

    print(f"Merged {len(labeled_files)} dataset(s): {before} rows -> {len(combined)} after "
          f"dropping {deduped} duplicate transaction_id(s).")
    print(f"Wrote {OUTPUT}")
    print()
    print("Category distribution:")
    print(combined["category"].value_counts().to_string())


if __name__ == "__main__":
    main()
