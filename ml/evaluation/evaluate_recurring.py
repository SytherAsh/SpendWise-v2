"""Recurring-payment classifier evaluation script (ML strategy phase,
2026-07-11). Mirrors evaluate.py's shape: fits a fresh model on an 80/20
held-out split of *candidate groups* (not raw transactions) rather than
scoring the committed artifact, for an honest, non-leaked accuracy estimate.

    cd ml
    python evaluation/evaluate_recurring.py --data data/spendwise_labeled.xlsx

Interpreting these numbers: because training labels are bootstrapped from the
existing strict rule (training/recurring_labels.py), high accuracy here means
"the classifier reproduces the strict rule's judgment on held-out candidate
groups" -- not "the classifier discovers recurring patterns no rule has ever
seen." That's expected and fine for a first version; it's what makes this
different from re-implementing the strict rule is that it generalizes over
continuous features instead of hard cutoffs (see recurring_labels.py).
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

from training.model_pipeline import RECURRING_FEATURE_COLUMNS, build_recurring_pipeline  # noqa: E402
from training.train import load_labeled_dataset  # noqa: E402
from training.train_recurring import DEFAULT_DATA_PATH, build_training_frame  # noqa: E402

DEFAULT_REPORTS_DIR = Path(__file__).resolve().parent / "reports"
LABELS = [0, 1]


def run_evaluation(
    data_path: Path = DEFAULT_DATA_PATH,
    save_report: bool = True,
    reports_dir: Path = DEFAULT_REPORTS_DIR,
) -> dict:
    df = load_labeled_dataset(data_path)
    training_frame = build_training_frame(df)

    x = training_frame[RECURRING_FEATURE_COLUMNS]
    y = training_frame["label"]

    try:
        x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=42, stratify=y)
    except ValueError:
        # Too few positive/negative candidates to stratify -- fall back to a
        # plain random split rather than crashing (same fallback pattern as
        # evaluate.py).
        x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=42)

    model = build_recurring_pipeline()
    model.fit(x_train, y_train)

    y_pred = model.predict(x_test)
    confidences = model.predict_proba(x_test).max(axis=1)

    accuracy = float((y_pred == y_test.to_numpy()).mean())
    precision, recall, f1, support = precision_recall_fscore_support(y_test, y_pred, labels=LABELS, zero_division=0)

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "n_candidate_groups": int(len(training_frame)),
        "n_test_samples": int(len(y_test)),
        "accuracy": accuracy,
        "precision_recurring": float(precision[1]),
        "recall_recurring": float(recall[1]),
        "f1_recurring": float(f1[1]),
        "support_recurring": int(support[1]),
        "support_not_recurring": int(support[0]),
        "confusion_matrix": confusion_matrix(y_test, y_pred, labels=LABELS).tolist(),
        "confusion_matrix_labels": LABELS,
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
        report_file = reports_dir / f"evaluation_recurring_{timestamp}.json"
        report_file.write_text(json.dumps(report, indent=2))
        report_path = str(report_file)

    report["report_path"] = report_path
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate the SpendWise recurring-payment classifier.")
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA_PATH)
    args = parser.parse_args()

    report = run_evaluation(data_path=args.data)
    print(f"Accuracy: {report['accuracy']:.4f} over {report['n_test_samples']} held-out candidate groups")
    print(f"Recurring precision/recall/f1: {report['precision_recurring']:.3f} / {report['recall_recurring']:.3f} / {report['f1_recurring']:.3f}")
    print(f"Report saved to {report['report_path']}")


if __name__ == "__main__":
    main()
