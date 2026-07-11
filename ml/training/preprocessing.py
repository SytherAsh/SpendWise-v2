"""Raw transaction dict -> feature vector (E4-S2-T1).

Extracts and normalizes recipient_name, upi_id, bank, transaction_mode, amount,
and note per docs/operations/testing.md Section 2 ("Preprocessing pipeline" /
"Feature extraction"). Nulls are handled gracefully — per docs/spec/database.md's
"Notes from Real Data", upi_id/bank/note/transaction_mode are all commonly null
in real SMS and bank-statement data, so every text field degrades to "" rather
than raising.

Also joins the counterparty_type/counterparty_confidence features (categorization
strengthening, 2026-07-11) via training/counterparty.py — see that module's
docstring for what these represent and why.
"""

import pandas as pd

from training.counterparty import lookup_counterparty

_NULL_TOKENS = {"nan", "none", "n/a", "null"}


def _clean_text(value: object) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    if text.lower() in _NULL_TOKENS:
        return ""
    return text


def _clean_amount(value: object) -> float:
    if value is None:
        return 0.0
    try:
        amount = float(value)
    except (TypeError, ValueError):
        return 0.0
    if amount != amount:  # NaN check without importing math/numpy here
        return 0.0
    return amount


def extract_features(transaction: dict) -> dict:
    """Normalize one raw transaction dict into the feature dict the model's
    ColumnTransformer expects. Never raises — missing/null fields degrade to
    "" (text) or 0.0 (amount)."""
    recipient_name = _clean_text(transaction.get("recipient_name"))
    upi_id = _clean_text(transaction.get("upi_id"))
    bank = _clean_text(transaction.get("bank"))
    transaction_mode = _clean_text(transaction.get("transaction_mode"))
    note = _clean_text(transaction.get("note"))
    amount = _clean_amount(transaction.get("amount"))

    text = " ".join(part for part in (recipient_name, upi_id, note) if part)
    counterparty_type, counterparty_confidence = lookup_counterparty(recipient_name, upi_id)

    return {
        "recipient_name": recipient_name,
        "upi_id": upi_id,
        "bank": bank,
        "transaction_mode": transaction_mode,
        "note": note,
        "amount": amount,
        "text": text,
        "counterparty_type": counterparty_type,
        "counterparty_confidence": counterparty_confidence,
    }


def build_feature_frame(transactions: list[dict]) -> pd.DataFrame:
    """Batch version of extract_features for training/evaluation."""
    rows = [extract_features(t) for t in transactions]
    return pd.DataFrame(
        rows,
        columns=[
            "recipient_name",
            "upi_id",
            "bank",
            "transaction_mode",
            "note",
            "amount",
            "text",
            "counterparty_type",
            "counterparty_confidence",
        ],
    )
