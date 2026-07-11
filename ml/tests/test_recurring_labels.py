from datetime import date

from training.recurring_features import Txn
from training.recurring_labels import bootstrap_label


def _txn(day: str, amount: float) -> Txn:
    return Txn(date=date.fromisoformat(day), amount=amount)


def test_bootstrap_label_positive_when_strict_rule_also_qualifies() -> None:
    window = [_txn("2023-01-01", 800.0), _txn("2023-01-31", 810.0), _txn("2023-03-01", 795.0)]
    assert bootstrap_label(window) == 1


def test_bootstrap_label_negative_for_loose_only_candidate() -> None:
    # Only 2 occurrences -- passes loose Stage 1's min_count of 2, but not the
    # strict rule's min_count of 3.
    window = [_txn("2023-01-01", 800.0), _txn("2023-01-31", 810.0)]
    assert bootstrap_label(window) == 0


def test_bootstrap_label_negative_when_spread_beyond_strict_window() -> None:
    window = [_txn("2023-01-01", 800.0), _txn("2023-04-01", 800.0), _txn("2023-07-01", 800.0)]
    assert bootstrap_label(window) == 0
