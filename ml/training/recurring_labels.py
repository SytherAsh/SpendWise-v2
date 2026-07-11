"""Bootstrap labels for the recurring-payment Stage 2 classifier (2026-07-11).

There is no `recurring_corrections` table yet -- that's the production
confirm/dismiss feedback loop (Alerts module, not yet built). Without it,
there is no real-world "the user confirmed this was/wasn't recurring" signal
to train on, the same problem categorization had before the manual labeling
interview (`ml/labeling/`). The bootstrap here plays the same role
merchant_rules.csv played for categorization before that interview: a weak,
rule-derived label that gets a real classifier off the ground now, to be
replaced/refined by real user corrections once the production feedback loop
exists (adaptive supervised learning, ADR-003/004 in docs/spec/decisions.md).

Label definition: a loose Stage 1 candidate (recurring_features.py) is
labeled positive if it *also* satisfies the existing strict rule
(RecurringPaymentDetector.java's thresholds) -- i.e. the classifier is
trained to reproduce the strict rule's judgment, but over continuous
features (interval_cv, amount_cv, ...) instead of hard cutoffs, so it can
generalize to nearby cases the hard rule would reject outright (e.g. 65 days
instead of 60, or 12% amount drift instead of 10%). It cannot yet discover
recurring patterns the strict rule has never seen an example of -- that
requires real corrections, not this bootstrap. Documented here rather than
glossed over: this is a real, known limitation of training on distant
supervision, not an oversight.
"""

from training.recurring_features import (
    STRICT_AMOUNT_TOLERANCE,
    STRICT_MAX_WINDOW_DAYS,
    STRICT_MIN_COUNT,
    Txn,
    find_qualifying_windows,
)


def bootstrap_label(window: list[Txn]) -> int:
    """1 if this candidate window also contains a strict-rule-qualifying
    sub-window, else 0."""
    strict_windows = find_qualifying_windows(window, STRICT_MIN_COUNT, STRICT_MAX_WINDOW_DAYS, STRICT_AMOUNT_TOLERANCE)
    return 1 if strict_windows else 0
