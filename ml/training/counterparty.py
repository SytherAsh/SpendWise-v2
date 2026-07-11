"""Counterparty-type feature lookup (categorization strengthening, 2026-07-11).

Joins recipient_name/upi_id against ml/labeling/knowledge_base/counterparty_knowledge.csv
to produce two input features for the categorization pipeline -- counterparty_type and
counterparty_confidence -- used to help Stage 1 (Transfer vs. Spend, see model_pipeline.py)
distinguish "paying a person" from "paying a merchant." This is an input feature only:
counterparty type is never a predicted output category, per ADR-010 in
docs/spec/decisions.md.

Matching mirrors merchant_rules.csv's convention (case-insensitive substring
containment against recipient_name or upi_id per each row's match_field, first
match in file order wins) rather than inventing a different rule -- see
ml/labeling/knowledge_base/SCHEMA.md and counterparty_knowledge.README.md.
"""

from functools import lru_cache
from pathlib import Path

import pandas as pd

KNOWLEDGE_BASE_PATH = (
    Path(__file__).resolve().parent.parent / "labeling" / "knowledge_base" / "counterparty_knowledge.csv"
)

UNKNOWN_TYPE = "unknown"
NO_CONFIDENCE = "none"


@lru_cache(maxsize=1)
def _load_rules() -> tuple[tuple[str, str, str, str], ...]:
    """Returns (pattern_lower, match_field, counterparty_type, confidence) rows,
    in file order (first match wins). Cached for the process lifetime -- this is
    a static, committed repo file, same caching approach as api/model_store.py's
    in-memory model cache."""
    if not KNOWLEDGE_BASE_PATH.exists():
        return ()
    df = pd.read_csv(KNOWLEDGE_BASE_PATH)
    return tuple(
        (str(row.recipient_pattern).lower(), row.match_field, row.counterparty_type, row.confidence)
        for row in df.itertuples(index=False)
    )


def lookup_counterparty(recipient_name: str, upi_id: str) -> tuple[str, str]:
    """Returns (counterparty_type, confidence) for the first matching rule, or
    (UNKNOWN_TYPE, NO_CONFIDENCE) if nothing matches -- including recipients
    outside this knowledge base entirely (new users, new merchants)."""
    recipient_lower = (recipient_name or "").lower()
    upi_lower = (upi_id or "").lower()

    for pattern, match_field, counterparty_type, confidence in _load_rules():
        if not pattern:
            continue
        field_value = recipient_lower if match_field == "recipient_name" else upi_lower
        if pattern in field_value:
            return counterparty_type, confidence

    return UNKNOWN_TYPE, NO_CONFIDENCE
