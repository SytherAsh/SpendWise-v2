"""Shared scikit-learn pipeline definition — the classifier architecture used
by both training/train.py and evaluation/evaluate.py, so a retrain (E4-S2-T4)
and an evaluation run (E4-S2-T5) always score the same model shape.

RandomForestClassifier with class_weight="balanced": the labeled dataset is
heavily skewed (Transfers ~50%, several categories under 30 examples, Sports &
Fitness has zero — see ml/labeling/tracking/EPIC_4_HANDOFF.md) and a random
forest needs no feature scaling for the mixed text/categorical/numeric input
below, unlike a linear model.
"""

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

FEATURE_COLUMNS = ["text", "bank", "transaction_mode", "amount"]


def build_pipeline() -> Pipeline:
    column_transformer = ColumnTransformer(
        transformers=[
            ("text", TfidfVectorizer(ngram_range=(1, 2), min_df=1), "text"),
            ("categorical", OneHotEncoder(handle_unknown="ignore"), ["bank", "transaction_mode"]),
            ("amount", "passthrough", ["amount"]),
        ]
    )
    classifier = RandomForestClassifier(
        n_estimators=300,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1,
    )
    return Pipeline([("features", column_transformer), ("classifier", classifier)])
