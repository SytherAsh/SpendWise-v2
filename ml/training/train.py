"""Baseline scikit-learn classifier training script (E4-S2-T2).

    cd ml
    python training/train.py --output models/

Trains on the labeled dataset (docs/spec/decisions.md ADR-003/ADR-004: adaptive
supervised learning, server-side inference, scikit-learn — not deep learning,
not on-device) and writes the artifact into --output, which is committed to
the repo at MODEL_PATH per docs/operations/deployment.md's "Model artifacts"
note (no external model store at MVP).

`--data` defaults to whichever `.csv`/`.xlsx` file is newest in `ml/data/`
(see `training/dataset_locator.py` and ADR-017) — no filename is hardcoded,
so dropping in a new export never requires a code change.

Trains the hierarchical Stage 1 (Transfer vs. Spend) + Stage 2 (spend
sub-category) model described in training/model_pipeline.py (categorization
strengthening, 2026-07-11). One HierarchicalCategoryModel is still saved as
a single artifact, so this stays a drop-in change for everything downstream.

train_model() is also called directly by api/retrain.py (E4-S2-T4) so the CLI
and the /retrain endpoint share one training code path.
"""

import argparse
import sys
from pathlib import Path

import joblib
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from api.categories import category_id_for_name  # noqa: E402
from api.config import MODEL_FILENAME  # noqa: E402
from training.dataset_locator import find_latest_dataset_file  # noqa: E402
from training.model_pipeline import (  # noqa: E402
    HierarchicalCategoryModel,
    build_subcategory_pipeline,
    build_transfer_pipeline,
)
from training.preprocessing import build_feature_frame  # noqa: E402

DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent.parent / "models"


def _normalize_columns(df: pd.DataFrame) -> pd.DataFrame:
    """Column names are export-convention-dependent (the current file uses
    `Transaction_Date`/`Recipient_Name`/`UPI_ID`; every reader in this codebase
    — build_feature_frame, train_model, train_recurring — expects lowercase
    snake_case). Normalizing once here, rather than requiring every future
    export to match an exact casing, is what makes "swap in a new CSV with no
    code change" actually true instead of true-until-the-next-export's-headers-
    differ. Case and whitespace only — column *names* (recipient_name, amount,
    category, ...) still have to match what the pipeline expects; this doesn't
    invent a semantic mapping for a renamed or missing column."""
    df = df.copy()
    df.columns = [str(c).strip().lower().replace(" ", "_") for c in df.columns]
    return df


def load_labeled_dataset(data_path: Path | None = None) -> pd.DataFrame:
    """`data_path=None` (the default for every caller — CLI scripts, /retrain,
    /evaluate) resolves to whatever `find_latest_dataset_file` finds at call
    time, not at import time — a resolved-once-at-import default would go
    stale the moment a running `--reload` process outlives a file swap."""
    if data_path is None:
        data_path = find_latest_dataset_file()
    if data_path.suffix.lower() == ".csv":
        df = pd.read_csv(data_path)
    else:
        df = pd.read_excel(data_path)
    return _normalize_columns(df)


def train_model(df: pd.DataFrame):
    """Fit a fresh HierarchicalCategoryModel on a DataFrame with a `category`
    name column plus the raw transaction fields (recipient_name, upi_id, bank,
    transaction_mode, amount, note). Returns (model, n_samples).

    Stage 1 (transfer_pipeline) trains on every row, labeled Transfer/Spend.
    Stage 2 (subcategory_pipeline) trains only on the non-Transfer subset,
    labeled by the full 11-category id -- it never sees a Transfers example,
    so it can't accidentally reproduce the confusion Stage 1 exists to fix."""
    records = df.to_dict(orient="records")
    features = build_feature_frame(records)
    category_names = df["category"].astype(str).str.strip().reset_index(drop=True)
    category_ids = category_names.map(category_id_for_name)

    is_transfer = (category_names == "Transfers").astype(int)
    transfer_pipeline = build_transfer_pipeline()
    transfer_pipeline.fit(features, is_transfer)

    spend_mask = (is_transfer == 0).to_numpy()
    subcategory_pipeline = None
    if spend_mask.any():
        subcategory_pipeline = build_subcategory_pipeline()
        subcategory_pipeline.fit(features.iloc[spend_mask.nonzero()[0]], category_ids[spend_mask])

    model = HierarchicalCategoryModel(transfer_pipeline, subcategory_pipeline)
    return model, len(df)


def save_model(pipeline, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    model_path = output_dir / MODEL_FILENAME
    joblib.dump(pipeline, model_path)
    return model_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the SpendWise category classifier.")
    parser.add_argument("--data", type=Path, default=None, help="Defaults to the newest .csv/.xlsx in ml/data/")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    df = load_labeled_dataset(args.data)
    pipeline, n_samples = train_model(df)
    model_path = save_model(pipeline, args.output)
    print(f"Trained on {n_samples} samples. Model saved to {model_path}")


if __name__ == "__main__":
    main()
