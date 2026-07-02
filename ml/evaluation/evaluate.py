"""Model evaluation script (E4-S2-T5).

    cd ml
    python evaluation/evaluate.py --data data/spendwise_labeled.xlsx

Must be re-run after every retraining cycle (docs/testing.md §2). Fits a
fresh model on an internal 80/20 held-out split rather than scoring the
committed production artifact (which is trained on 100% of the data for
maximum real-world coverage per E4-S2-T2) — this keeps the reported accuracy
an honest, non-leaked estimate instead of train-set performance.
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
from training.model_pipeline import build_pipeline  # noqa: E402
from training.preprocessing import build_feature_frame  # noqa: E402
from training.train import load_labeled_dataset  # noqa: E402

DEFAULT_DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "spendwise_labeled.xlsx"
DEFAULT_REPORTS_DIR = Path(__file__).resolve().parent / "reports"
ALL_CATEGORY_IDS = list(range(1, len(CATEGORIES) + 1))


def run_evaluation(
    data_path: Path = DEFAULT_DATA_PATH,
    save_report: bool = True,
    reports_dir: Path = DEFAULT_REPORTS_DIR,
) -> dict:
    df = load_labeled_dataset(data_path)
    records = df.to_dict(orient="records")
    features = build_feature_frame(records)
    labels = df["category"].astype(str).str.strip().map(category_id_for_name)

    try:
        x_train, x_test, y_train, y_test = train_test_split(
            features, labels, test_size=0.2, random_state=42, stratify=labels
        )
    except ValueError:
        # A class with a single example can't be stratified — fall back to a
        # plain random split rather than crashing the evaluation run.
        x_train, x_test, y_train, y_test = train_test_split(features, labels, test_size=0.2, random_state=42)

    pipeline = build_pipeline()
    pipeline.fit(x_train, y_train)

    y_pred = pipeline.predict(x_test)
    y_proba = pipeline.predict_proba(x_test)
    confidences = y_proba.max(axis=1)

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
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA_PATH)
    args = parser.parse_args()

    report = run_evaluation(data_path=args.data)
    print(f"Accuracy: {report['accuracy']:.4f} over {report['n_samples']} held-out samples")
    print(f"Report saved to {report['report_path']}")


if __name__ == "__main__":
    main()
