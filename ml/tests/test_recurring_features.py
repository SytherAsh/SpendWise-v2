from datetime import date

from training.recurring_features import (
    Txn,
    compute_features,
    derive_cadence,
    find_loose_candidates,
    find_qualifying_windows,
    group_by_merchant,
)


def test_group_by_merchant_uses_upi_id_over_recipient_name() -> None:
    transactions = [
        {"transaction_date": "2023-01-01", "amount": -100.0, "upi_id": "swiggy@okicici", "recipient_name": "Swiggy"},
        {"transaction_date": "2023-02-01", "amount": -100.0, "upi_id": "swiggy@okicici", "recipient_name": "Swiggy Ltd"},
    ]
    groups = group_by_merchant(transactions)
    assert list(groups.keys()) == ["swiggy@okicici"]
    assert len(groups["swiggy@okicici"]) == 2


def test_group_by_merchant_excludes_credits_and_keyless_rows() -> None:
    transactions = [
        {"transaction_date": "2023-01-01", "amount": 5000.0, "upi_id": "x", "recipient_name": "Salary"},  # credit
        {"transaction_date": "2023-01-02", "amount": -50.0, "upi_id": None, "recipient_name": None},  # no key
        {"transaction_date": "2023-01-03", "amount": -50.0, "upi_id": None, "recipient_name": "Gym"},  # falls back to name
    ]
    groups = group_by_merchant(transactions)
    assert list(groups.keys()) == ["Gym"]


def _txn(day: str, amount: float) -> Txn:
    return Txn(date=date.fromisoformat(day), amount=amount)


def test_find_qualifying_windows_finds_three_within_tolerance_and_window() -> None:
    txns = [_txn("2023-01-01", 800.0), _txn("2023-01-31", 810.0), _txn("2023-03-01", 795.0)]
    windows = find_qualifying_windows(txns, min_count=3, max_window_days=60, amount_tolerance=0.10)
    assert len(windows) == 1
    assert len(windows[0]) == 3


def test_find_qualifying_windows_rejects_too_few() -> None:
    txns = [_txn("2023-01-01", 800.0), _txn("2023-01-31", 810.0)]
    windows = find_qualifying_windows(txns, min_count=3, max_window_days=60, amount_tolerance=0.10)
    assert windows == []


def test_find_qualifying_windows_rejects_spread_beyond_max_window() -> None:
    txns = [_txn("2023-01-01", 800.0), _txn("2023-04-01", 800.0), _txn("2023-07-01", 800.0)]
    windows = find_qualifying_windows(txns, min_count=3, max_window_days=60, amount_tolerance=0.10)
    assert windows == []


def test_find_loose_candidates_catches_a_quarterly_pattern_the_strict_rule_would_miss() -> None:
    # ~90-day spacing, comfortably within loose params, well outside the
    # strict 60-day window -- this is exactly the case the loosened Stage 1
    # exists to surface as a candidate.
    txns = [_txn("2023-01-01", 1200.0), _txn("2023-04-01", 1200.0), _txn("2023-07-01", 1200.0), _txn("2023-10-01", 1200.0)]
    loose = find_loose_candidates(txns)
    assert len(loose) == 1
    assert len(loose[0]) == 4

    strict = find_qualifying_windows(txns, min_count=3, max_window_days=60, amount_tolerance=0.10)
    assert strict == []


def test_compute_features_basic_stats() -> None:
    window = [_txn("2023-01-01", 1000.0), _txn("2023-02-01", 1000.0), _txn("2023-03-03", 1000.0)]
    features = compute_features(window, as_of=date.fromisoformat("2023-03-10"))

    assert features["occurrence_count"] == 3
    assert features["amount_mean"] == 1000.0
    assert features["amount_cv"] == 0.0  # identical amounts
    assert features["interval_mean_days"] > 0
    assert features["span_days"] == 61  # Jan 1 -> Mar 3
    assert features["days_since_last_occurrence"] == 7  # Mar 3 -> Mar 10


def test_derive_cadence_buckets() -> None:
    assert derive_cadence(7) == "weekly"
    assert derive_cadence(30) == "monthly"
    assert derive_cadence(90) == "quarterly"
    assert derive_cadence(365) == "annual"
    assert derive_cadence(45) == "irregular"
