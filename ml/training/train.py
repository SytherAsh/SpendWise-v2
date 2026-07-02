"""Baseline scikit-learn classifier training script (E4-S2-T2).

    cd ml
    python training/train.py --output models/

Trains on the labeled dataset (docs/decisions.md ADR-003/ADR-004: adaptive
supervised learning, server-side inference, scikit-learn — not deep learning,
not on-device) and writes the artifact into --output, which is committed to
the repo at MODEL_PATH per docs/deployment.md's "Model artifacts" note (no
external model store at MVP).

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
from training.model_pipeline import build_pipeline  # noqa: E402
from training.preprocessing import build_feature_frame  # noqa: E402

DEFAULT_DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "spendwise_labeled.xlsx"
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent.parent / "models"


def load_labeled_dataset(data_path: Path) -> pd.DataFrame:
    return pd.read_excel(data_path)


def train_model(df: pd.DataFrame):
    """Fit a fresh pipeline on a DataFrame with a `category` name column plus
    the raw transaction fields (recipient_name, upi_id, bank, transaction_mode,
    amount, note). Returns (pipeline, n_samples)."""
    records = df.to_dict(orient="records")
    features = build_feature_frame(records)
    labels = df["category"].astype(str).str.strip().map(category_id_for_name)

    pipeline = build_pipeline()
    pipeline.fit(features, labels)
    return pipeline, len(df)


def save_model(pipeline, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    model_path = output_dir / MODEL_FILENAME
    joblib.dump(pipeline, model_path)
    return model_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the SpendWise category classifier.")
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA_PATH)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    df = load_labeled_dataset(args.data)
    pipeline, n_samples = train_model(df)
    model_path = save_model(pipeline, args.output)
    print(f"Trained on {n_samples} samples. Model saved to {model_path}")


if __name__ == "__main__":
    main()
