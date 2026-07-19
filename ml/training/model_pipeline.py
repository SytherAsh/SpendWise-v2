"""Shared scikit-learn pipeline definitions -- the classifier architecture used
by both training/train.py and evaluation/evaluate.py, so a retrain (E4-S2-T4)
and an evaluation run (E4-S2-T5) always score the same model shape.

Hierarchical two-stage design (categorization strengthening, 2026-07-11):
Stage 1 decides Transfer vs. Spend; Stage 2, only ever run on rows Stage 1
called Spend, decides which of the 11 spend sub-categories. Both stages are
RandomForestClassifier(class_weight="balanced") -- the labeled dataset is
heavily skewed (Transfers ~50%, several categories under 30 examples, Sports &
Fitness has zero -- see ml/labeling/tracking/EPIC_4_HANDOFF.md) and a random
forest needs no feature scaling for the mixed text/categorical/numeric input
below, unlike a linear model.

Why hierarchical: the single flat 12-class model confused Transfers with its
two closest look-alikes, Food / Dine Out and Groceries (see
ml/evaluation/reports/evaluation_20260705T170803Z.json's confusion matrix) --
a P2P payment to a friend for a shared meal and an actual restaurant payment
share the same amount range and a person-shaped recipient_name. Isolating the
Transfer-vs-Spend boundary into its own stage, fed by a dedicated
counterparty_type/counterparty_confidence signal (training/counterparty.py)
that the flat model never had, targets exactly that confusion instead of
diluting it across an 11-way decision.
"""

import numpy as np
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

from api.categories import CATEGORIES, category_id_for_name

FEATURE_COLUMNS = ["text", "bank", "transaction_mode", "amount", "counterparty_type", "counterparty_confidence"]
TRANSFER_CATEGORY_ID = category_id_for_name("Transfers")


def _build_column_transformer() -> ColumnTransformer:
    return ColumnTransformer(
        transformers=[
            ("text", TfidfVectorizer(ngram_range=(1, 2), min_df=1), "text"),
            (
                "categorical",
                OneHotEncoder(handle_unknown="ignore"),
                ["bank", "transaction_mode", "counterparty_type", "counterparty_confidence"],
            ),
            ("amount", "passthrough", ["amount"]),
        ]
    )


def _build_random_forest(n_estimators: int = 300) -> RandomForestClassifier:
    return RandomForestClassifier(n_estimators=n_estimators, class_weight="balanced", random_state=42, n_jobs=-1)


def build_transfer_pipeline() -> Pipeline:
    """Stage 1: binary Transfer (1) vs. Spend (0)."""
    return Pipeline([("features", _build_column_transformer()), ("classifier", _build_random_forest())])


def build_subcategory_pipeline() -> Pipeline:
    """Stage 2: which of the 11 non-Transfer categories. Only ever fit/predicted
    on rows Stage 1 called Spend."""
    return Pipeline([("features", _build_column_transformer()), ("classifier", _build_random_forest())])


# Recurring-payment detection (ml/training/recurring_features.py) -- the Stage 2
# classifier for "is this candidate group actually recurring." All inputs are
# already-numeric statistics (interval/amount mean & coefficient of variation,
# occurrence count, span, recency), so unlike categorization's pipelines there
# is no text/categorical preprocessing step -- a bare RandomForestClassifier is
# the whole "pipeline."
RECURRING_FEATURE_COLUMNS = [
    "occurrence_count",
    "interval_mean_days",
    "interval_cv",
    "amount_mean",
    "amount_cv",
    "span_days",
    "days_since_last_occurrence",
]


def build_recurring_pipeline() -> RandomForestClassifier:
    # Fewer trees than categorization's 300 -- far fewer training examples
    # (candidate groups, not individual transactions) and only 7 features, so
    # 300 trees would mostly be redundant.
    return _build_random_forest(n_estimators=150)


class HierarchicalCategoryModel:
    """Chains Stage 1 (transfer_pipeline) and Stage 2 (subcategory_pipeline)
    behind the same interface the single flat model used to expose
    (predict/predict_proba), so api/model_store.py, api/retrain.py, and the
    joblib save/load path need no changes -- this is a drop-in replacement for
    the artifact they already load, not a new artifact shape."""

    def __init__(self, transfer_pipeline: Pipeline, subcategory_pipeline: Pipeline | None):
        self.transfer_pipeline = transfer_pipeline
        self.subcategory_pipeline = subcategory_pipeline

    def _transfer_proba(self, X) -> np.ndarray:
        """P(Transfer) per row. Falls back to all-zero if the fitted Stage 1
        classifier never saw a Transfer example (degenerate single-class fit,
        possible on tiny/synthetic datasets) rather than crashing on a missing
        class column."""
        proba = self.transfer_pipeline.predict_proba(X)
        classes = list(self.transfer_pipeline.classes_)
        if 1 not in classes:
            return np.zeros(len(X))
        return proba[:, classes.index(1)]

    def predict_with_confidence(self, X) -> tuple[np.ndarray, np.ndarray]:
        """Returns (category_ids, confidences). confidence is Stage 1's
        probability for a Transfer prediction, or Stage 2's probability alone
        (not combined with Stage 1's) for a Spend prediction -- Stage 2 already
        knows it's looking at a spend transaction, so diluting its confidence by
        Stage 1's would misrepresent how sure the sub-category call actually is."""
        X = X.reset_index(drop=True)
        n = len(X)
        transfer_proba = self._transfer_proba(X)
        is_transfer = transfer_proba >= 0.5

        category_ids = np.empty(n, dtype=int)
        confidences = np.empty(n, dtype=float)
        category_ids[is_transfer] = TRANSFER_CATEGORY_ID
        confidences[is_transfer] = transfer_proba[is_transfer]

        spend_mask = ~is_transfer
        if spend_mask.any():
            if self.subcategory_pipeline is None:
                # No non-Transfer training examples were available to fit Stage 2
                # (degenerate edge case) -- fall back to Transfers rather than
                # raising, consistent with _transfer_proba's fallback above.
                category_ids[spend_mask] = TRANSFER_CATEGORY_ID
                confidences[spend_mask] = 0.0
            else:
                spend_rows = X.iloc[spend_mask.nonzero()[0]]
                sub_pred = self.subcategory_pipeline.predict(spend_rows)
                sub_proba = self.subcategory_pipeline.predict_proba(spend_rows)
                category_ids[spend_mask] = sub_pred
                confidences[spend_mask] = sub_proba.max(axis=1)

        return category_ids, confidences

    def predict(self, X) -> np.ndarray:
        category_ids, _ = self.predict_with_confidence(X)
        return category_ids

    def set_inference_n_jobs(self, n_jobs: int) -> None:
        """Overrides both stages' `RandomForestClassifier.n_jobs` after the model is
        loaded, independent of whatever `n_jobs` the artifact was fit with.

        `n_jobs=-1` (the fit-time default -- see `_build_random_forest`) is the right
        call for *fitting* 300 trees across a full training set. But the same
        attribute also governs `predict()`/`predict_proba()`, and on Windows
        joblib's process-based `loky` backend (no `fork()` available) pays a large
        per-call worker-startup cost that dwarfs the actual work of running 300
        trees over a single request's one row. Measured on this project's model:
        ~1.0-1.5s per `/predict` call at `n_jobs=-1` vs. ~0.2-0.3s at `n_jobs=1` for
        a single row -- the entire difference is parallelization overhead, not
        model work.
        """
        self.transfer_pipeline.named_steps["classifier"].n_jobs = n_jobs
        if self.subcategory_pipeline is not None:
            self.subcategory_pipeline.named_steps["classifier"].n_jobs = n_jobs

    def predict_proba(self, X) -> np.ndarray:
        """Full joint distribution over all 12 categories (sums to 1 per row),
        via the law of total probability: P(cat) = P(Transfer) for Transfers,
        P(not Transfer) * P(cat | not Transfer) for every other category. This
        is a generic, honest probability distribution for any duck-typed
        consumer -- distinct from predict_with_confidence's "confidence" number,
        which deliberately does NOT dilute Stage 2's confidence by Stage 1's."""
        X = X.reset_index(drop=True)
        n = len(X)
        proba = np.zeros((n, len(CATEGORIES)))
        transfer_proba = self._transfer_proba(X)

        if self.subcategory_pipeline is None:
            # No non-Transfer training examples were available to fit Stage 2 --
            # same degenerate-edge-case fallback as predict_with_confidence:
            # treat every row as Transfers so the distribution still sums to 1.
            proba[:, TRANSFER_CATEGORY_ID - 1] = 1.0
            return proba

        proba[:, TRANSFER_CATEGORY_ID - 1] = transfer_proba
        not_transfer_proba = 1.0 - transfer_proba
        sub_proba = self.subcategory_pipeline.predict_proba(X)
        for i, category_id in enumerate(self.subcategory_pipeline.classes_):
            proba[:, category_id - 1] = not_transfer_proba * sub_proba[:, i]

        return proba
