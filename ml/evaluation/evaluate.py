"""Model evaluation script (E4-S2-T5).

    cd ml
    python evaluation/evaluate.py

`--data` defaults to whichever `.csv`/`.xlsx` file is newest in `ml/data/`
(see `training/dataset_locator.py` and ADR-017) — no filename is hardcoded.

Must be re-run after every retraining cycle (docs/operations/testing.md §2).
Fits a fresh model on an internal 80/20 held-out split rather than scoring the
committed production artifact (which is trained on 100% of the data for
maximum real-world coverage per E4-S2-T2) — this keeps the reported accuracy
an honest, non-leaked estimate instead of train-set performance.

Splits the *rows* 80/20 first, then trains the full hierarchical model
(training/train.py's train_model(), Stage 1 + Stage 2 together) on the train
rows and scores it end-to-end on the held-out test rows — so `accuracy` here
is the honest, whole-pipeline number directly comparable to pre-hierarchy
reports (e.g. evaluation_20260705T170803Z.json's 0.917), not a per-stage
number that would overstate how the two stages perform chained together.
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support
from sklearn.model_selection import train_test_split

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from api.categories import CATEGORIES, category_id_for_name, category_name_for_id  # noqa: E402
from training.preprocessing import build_feature_frame  # noqa: E402
from training.train import load_labeled_dataset, train_model  # noqa: E402

DEFAULT_REPORTS_DIR = Path(__file__).resolve().parent / "reports"
ALL_CATEGORY_IDS = list(range(1, len(CATEGORIES) + 1))


def run_evaluation(
    data_path: Path | None = None,
    save_report: bool = True,
    reports_dir: Path = DEFAULT_REPORTS_DIR,
) -> dict:
    df = load_labeled_dataset(data_path)
    labels = df["category"].astype(str).str.strip().map(category_id_for_name)

    try:
        train_idx, test_idx = train_test_split(
            df.index.to_numpy(), test_size=0.2, random_state=42, stratify=labels
        )
    except ValueError:
        # A class with a single example can't be stratified — fall back to a
        # plain random split rather than crashing the evaluation run.
        train_idx, test_idx = train_test_split(df.index.to_numpy(), test_size=0.2, random_state=42)

    train_df = df.loc[train_idx].reset_index(drop=True)
    test_df = df.loc[test_idx].reset_index(drop=True)

    model, _ = train_model(train_df)

    x_test = build_feature_frame(test_df.to_dict(orient="records"))
    y_test = test_df["category"].astype(str).str.strip().map(category_id_for_name)

    y_pred = model.predict(x_test)
    _, confidences = model.predict_with_confidence(x_test)

    accuracy = float((y_pred == y_test.to_numpy()).mean())

    precision, recall, f1, support = precision_recall_fscore_support(
        y_test, y_pred, labels=ALL_CATEGORY_IDS, zero_division=0
    )
    per_category = [
        {
            "category_id": category_id,
            "category_name": category_name_for_id(category_id),
            "precision": float(precision[i]),
            "recall": float(recall[i]),
            "f1_score": float(f1[i]),
            "support": int(support[i]),
        }
        for i, category_id in enumerate(ALL_CATEGORY_IDS)
    ]

    matrix = confusion_matrix(y_test, y_pred, labels=ALL_CATEGORY_IDS)

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "n_samples": int(len(y_test)),
        "accuracy": accuracy,
        "per_category": per_category,
        "confusion_matrix": matrix.tolist(),
        "confusion_matrix_labels": ALL_CATEGORY_IDS,
        "confidence_distribution": {
            "mean": float(np.mean(confidences)),
            "median": float(np.median(confidences)),
            "min": float(np.min(confidences)),
            "max": float(np.max(confidences)),
        },
    }

    report_path = ""
    if save_report:
        reports_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        report_file = reports_dir / f"evaluation_{timestamp}.json"
        report_file.write_text(json.dumps(report, indent=2))
        report_path = str(report_file)

    report["report_path"] = report_path
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate the SpendWise category classifier.")
    parser.add_argument("--data", type=Path, default=None, help="Defaults to the newest .csv/.xlsx in ml/data/")
    args = parser.parse_args()

    report = run_evaluation(data_path=args.data)
    print(f"Accuracy: {report['accuracy']:.4f} over {report['n_samples']} held-out samples")
    print(f"Report saved to {report['report_path']}")


if __name__ == "__main__":
    main()
