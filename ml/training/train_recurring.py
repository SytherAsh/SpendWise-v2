"""Recurring-payment Stage 2 classifier training script (ML strategy phase,
2026-07-11).

    cd ml
    python training/train_recurring.py --output models/

Bootstrap-trains on the same labeled transaction data as categorization
(whichever `.csv`/`.xlsx` file `training/dataset_locator.py` finds newest in
`ml/data/` -- only transaction_date/amount/recipient_name/upi_id are used
here, not the category column), using loose Stage 1 candidate generation
(recurring_features.py) and strict-rule-derived weak labels
(recurring_labels.py) -- see that module's docstring for why this is a
bootstrap, not a ground-truth-labeled training set.
"""

import argparse
import sys
from pathlib import Path

import joblib
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from training.model_pipeline import build_recurring_pipeline  # noqa: E402
from training.recurring_features import (  # noqa: E402
    compute_features,
    find_loose_candidates,
    group_by_merchant,
)
from training.recurring_labels import bootstrap_label  # noqa: E402
from training.train import load_labeled_dataset  # noqa: E402

DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent.parent / "models"
MODEL_FILENAME = "recurring_classifier.joblib"


def build_training_frame(df: pd.DataFrame) -> pd.DataFrame:
    """One row per loose candidate group, with its feature vector and
    bootstrap label. `as_of` is pinned to the dataset's own last transaction
    date for every group (a single, realistic "today") rather than each
    group's own last occurrence, which would make days_since_last_occurrence
    constant (always 0) and useless as a feature."""
    records = df.to_dict(orient="records")
    as_of = pd.Timestamp(df["transaction_date"].max()).date()

    rows = []
    for merchant_txns in group_by_merchant(records).values():
        for window in find_loose_candidates(merchant_txns):
            features = compute_features(window, as_of=as_of)
            features["label"] = bootstrap_label(window)
            rows.append(features)
    return pd.DataFrame(rows)


def train_recurring_model(df: pd.DataFrame):
    """Returns (model, n_candidates, n_positive)."""
    training_frame = build_training_frame(df)
    from training.model_pipeline import RECURRING_FEATURE_COLUMNS

    x = training_frame[RECURRING_FEATURE_COLUMNS]
    y = training_frame["label"]

    model = build_recurring_pipeline()
    model.fit(x, y)
    return model, len(training_frame), int(y.sum())


def save_model(model, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    model_path = output_dir / MODEL_FILENAME
    joblib.dump(model, model_path)
    return model_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the SpendWise recurring-payment classifier.")
    parser.add_argument("--data", type=Path, default=None, help="Defaults to the newest .csv/.xlsx in ml/data/")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    df = load_labeled_dataset(args.data)
    model, n_candidates, n_positive = train_recurring_model(df)
    model_path = save_model(model, args.output)
    print(
        f"Trained on {n_candidates} candidate groups ({n_positive} positive, "
        f"{n_candidates - n_positive} negative). Model saved to {model_path}"
    )


if __name__ == "__main__":
    main()
