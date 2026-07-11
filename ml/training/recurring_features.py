"""Recurring-payment candidate generation and feature engineering (ML strategy
phase, 2026-07-11).

The production Java rule (`RecurringPaymentDetector`, E6-S1-T1,
docs/spec/requirements.md "Recurring payment detection rule") only ever
*outputs* groups that already pass a strict gate: 3+ debit transactions from
the same merchant, amounts within +/-10% of the group's minimum, within a
60-day window. Anything that doesn't pass never becomes a candidate at all --
which means a Stage 2 classifier layered only on top of that rule's output
could never catch anything the rule already misses (quarterly/annual
cadences, amount drift beyond 10%, a pattern visible after only 2
occurrences). This module exists to *loosen* candidate generation so those
borderline cases become candidates a classifier can judge, while still
reusing the same anchored-amount-clustering + rolling-window shape the Java
rule uses (mirrored here, not reinvented) so the two stay conceptually
close.

Debit-only, mirroring `RecurringCandidateTransaction.java` -- recurring
*payments* (EMIs, subscriptions, bills), not recurring credits.
"""

from datetime import date
from statistics import mean, stdev
from typing import NamedTuple

import pandas as pd

# Loose Stage 1 parameters -- deliberately wider than the strict rule below,
# so genuinely-recurring-but-irregular patterns become candidates instead of
# being silently rejected before any model sees them.
LOOSE_MIN_COUNT = 2
LOOSE_MAX_WINDOW_DAYS = 400  # comfortably covers an annual cadence (~365 days)
LOOSE_AMOUNT_TOLERANCE = 0.40  # +/-40%, anchored to the group's minimum amount

# The existing strict rule (RecurringPaymentDetector.java) -- reused here only
# to derive a bootstrap label for training, not as a serving-time gate.
STRICT_MIN_COUNT = 3
STRICT_MAX_WINDOW_DAYS = 60
STRICT_AMOUNT_TOLERANCE = 0.10


class Txn(NamedTuple):
    date: date
    amount: float  # magnitude (always positive) -- sign is discarded, debit-only by construction


def _clean_key_field(value: object) -> str | None:
    """None/NaN/blank -> None. pandas gives NaN (a truthy float) rather than
    None for missing values in an all-blank column read back from Excel, so a
    plain `if value:` check would treat a missing upi_id as present."""
    if value is None:
        return None
    if isinstance(value, float) and pd.isna(value):
        return None
    text = str(value).strip()
    return text or None


def _merchant_key(recipient_name: str | None, upi_id: str | None) -> str | None:
    """Same precedence as RecurringPaymentDetector.merchantKey: upi_id when
    present, else recipient_name."""
    upi = _clean_key_field(upi_id)
    if upi:
        return upi
    return _clean_key_field(recipient_name)


def group_by_merchant(transactions: list[dict]) -> dict[str, list[Txn]]:
    """Debit-only transactions, grouped by merchant key. Transactions with
    neither upi_id nor recipient_name are excluded (no grouping key), same as
    the Java rule."""
    groups: dict[str, list[Txn]] = {}
    for txn in transactions:
        amount = txn.get("amount")
        if amount is None or pd.isna(amount) or amount >= 0:
            continue  # credits, missing, and zero-amount rows are out of scope
        key = _merchant_key(txn.get("recipient_name"), txn.get("upi_id"))
        if key is None:
            continue
        # transaction_date arrives as a string from the labeled xlsx, but a
        # Timestamp/datetime/date from other sources (e.g. a live DB row) --
        # pd.Timestamp(...).date() normalizes all of them to a plain date.
        txn_date = pd.Timestamp(txn["transaction_date"]).date()
        groups.setdefault(key, []).append(Txn(date=txn_date, amount=abs(amount)))
    return groups


def _anchored_amount_clusters(txns: list[Txn], tolerance: float) -> list[list[Txn]]:
    """Sequential clustering on amount-ascending order, each cluster anchored
    to its own minimum -- mirrors anchoredAmountClusters() in
    RecurringPaymentDetector.java."""
    by_amount = sorted(txns, key=lambda t: t.amount)
    clusters: list[list[Txn]] = []
    current: list[Txn] = []
    ceiling = None
    for txn in by_amount:
        if ceiling is None or txn.amount > ceiling:
            current = []
            clusters.append(current)
            ceiling = txn.amount * (1 + tolerance)
        current.append(txn)
    return clusters


def find_qualifying_windows(
    txns: list[Txn],
    min_count: int,
    max_window_days: int,
    amount_tolerance: float,
) -> list[list[Txn]]:
    """First qualifying date-window per amount cluster -- mirrors
    qualifyingWindow() in RecurringPaymentDetector.java, parameterized so the
    same logic serves both loose candidate generation and strict-rule
    membership checks (used for bootstrap labeling, see recurring_labels.py)."""
    windows: list[list[Txn]] = []
    for cluster in _anchored_amount_clusters(txns, amount_tolerance):
        if len(cluster) < min_count:
            continue
        by_date = sorted(cluster, key=lambda t: t.date)
        for i in range(len(by_date)):
            window = []
            for j in range(i, len(by_date)):
                if (by_date[j].date - by_date[i].date).days > max_window_days:
                    break
                window.append(by_date[j])
            if len(window) >= min_count:
                windows.append(window)
                break  # first qualifying window per cluster, same as the Java rule
    return windows


def find_loose_candidates(txns: list[Txn]) -> list[list[Txn]]:
    return find_qualifying_windows(txns, LOOSE_MIN_COUNT, LOOSE_MAX_WINDOW_DAYS, LOOSE_AMOUNT_TOLERANCE)


def compute_features(window: list[Txn], as_of: date | None = None) -> dict:
    """Per-candidate-group statistics -- the actual input to Stage 2's
    classifier. `as_of` defaults to the window's own last date (used for
    training/eval, where "now" is arbitrary); production serving should pass
    the real current date so days_since_last_occurrence is meaningful."""
    dates = sorted(t.date for t in window)
    amounts = [t.amount for t in window]

    intervals = [(dates[i + 1] - dates[i]).days for i in range(len(dates) - 1)]
    interval_mean = mean(intervals) if intervals else 0.0
    interval_cv = (stdev(intervals) / interval_mean) if len(intervals) > 1 and interval_mean > 0 else 0.0

    amount_mean = mean(amounts)
    amount_cv = (stdev(amounts) / amount_mean) if len(amounts) > 1 and amount_mean > 0 else 0.0

    reference = as_of or dates[-1]

    return {
        "occurrence_count": len(window),
        "interval_mean_days": float(interval_mean),
        "interval_cv": float(interval_cv),
        "amount_mean": float(amount_mean),
        "amount_cv": float(amount_cv),
        "span_days": float((dates[-1] - dates[0]).days),
        "days_since_last_occurrence": float((reference - dates[-1]).days),
    }


CADENCE_BUCKETS: list[tuple[str, float, float]] = [
    ("weekly", 5, 9),
    ("biweekly", 12, 16),
    ("monthly", 25, 35),
    ("quarterly", 80, 100),
    ("annual", 350, 380),
]


def derive_cadence(interval_mean_days: float) -> str:
    """Deterministic, not learned -- once a group is judged recurring, which
    bucket its average interval falls into is a straightforward lookup, not a
    genuinely hard classification problem worth spending training data on."""
    for label, low, high in CADENCE_BUCKETS:
        if low <= interval_mean_days <= high:
            return label
    return "irregular"
