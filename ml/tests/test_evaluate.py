"""E4-S2-T5: evaluation/evaluate.py produces a report file with all required
metric sections; GET /evaluate returns the same metrics as JSON.
"""

import pandas as pd
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from evaluation.evaluate import DEFAULT_DATA_PATH, run_evaluation

TEST_KEY = "test-internal-key"

# Small synthetic dataset (enough rows per class for an 80/20 stratified
# split) so the fast tests don't have to fit against the full 1,810-row
# real dataset.
_SYNTHETIC_ROWS = []
for i in range(10):
    _SYNTHETIC_ROWS.append(
        {"recipient_name": f"Swiggy{i}", "upi_id": "swiggy@okicici", "bank": "ICICI", "transaction_mode": "UPI", "amount": -(200 + i), "note": None, "category": "Food / Dine Out"}
    )
    _SYNTHETIC_ROWS.append(
        {"recipient_name": f"DMart{i}", "upi_id": "dmart@okhdfc", "bank": "HDFC", "transaction_mode": "UPI", "amount": -(500 + i), "note": None, "category": "Groceries"}
    )


@pytest.fixture
def synthetic_data_path(tmp_path):
    path = tmp_path / "synthetic.xlsx"
    pd.DataFrame(_SYNTHETIC_ROWS).to_excel(path, index=False)
    return path


def test_run_evaluation_produces_report_file_with_required_sections(synthetic_data_path, tmp_path) -> None:
    reports_dir = tmp_path / "reports"

    report = run_evaluation(data_path=synthetic_data_path, save_report=True, reports_dir=reports_dir)

    assert "accuracy" in report
    assert "per_category" in report
    assert len(report["per_category"]) == 12
    assert "confusion_matrix" in report
    assert len(report["confusion_matrix"]) == 12
    assert len(report["confusion_matrix"][0]) == 12
    assert "confidence_distribution" in report
    assert {"mean", "median", "min", "max"} <= set(report["confidence_distribution"].keys())
    assert 0.0 <= report["accuracy"] <= 1.0

    report_files = list(reports_dir.glob("evaluation_*.json"))
    assert len(report_files) == 1
    assert report["report_path"] == str(report_files[0])


def test_run_evaluation_without_saving_skips_report_file(synthetic_data_path, tmp_path) -> None:
    reports_dir = tmp_path / "reports"

    report = run_evaluation(data_path=synthetic_data_path, save_report=False, reports_dir=reports_dir)

    assert report["report_path"] == ""
    assert not reports_dir.exists()


@pytest.mark.slow
@pytest.mark.skipif(
    not DEFAULT_DATA_PATH.exists(),
    reason="ml/data/spendwise_labeled.xlsx is gitignored (real personal data) — absent in CI checkouts.",
)
def test_run_evaluation_against_real_dataset_writes_report(tmp_path) -> None:
    reports_dir = tmp_path / "reports"

    report = run_evaluation(save_report=True, reports_dir=reports_dir)

    assert report["n_samples"] > 300
    assert len(list(reports_dir.glob("evaluation_*.json"))) == 1


def test_evaluate_endpoint_returns_metrics_as_json(monkeypatch: pytest.MonkeyPatch) -> None:
    import api.evaluate as evaluate_module

    fake_report = {
        "generated_at": "2026-07-02T00:00:00+00:00",
        "n_samples": 4,
        "accuracy": 0.75,
        "per_category": [
            {"category_id": i, "category_name": f"Cat{i}", "precision": 0.5, "recall": 0.5, "f1_score": 0.5, "support": 1}
            for i in range(1, 13)
        ],
        "confusion_matrix": [[0] * 12 for _ in range(12)],
        "confusion_matrix_labels": list(range(1, 13)),
        "confidence_distribution": {"mean": 0.6, "median": 0.6, "min": 0.4, "max": 0.9},
        "report_path": "/tmp/fake.json",
    }
    monkeypatch.setattr(evaluate_module, "run_evaluation", lambda: fake_report)

    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)
    from api.security import InternalKeyMiddleware

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(evaluate_module.router)
    client = TestClient(app)

    response = client.get("/evaluate", headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body["accuracy"] == 0.75
    assert body["n_samples"] == 4
    assert len(body["per_category"]) == 12


def test_evaluate_endpoint_requires_internal_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)
    import api.evaluate as evaluate_module
    from api.security import InternalKeyMiddleware

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(evaluate_module.router)
    client = TestClient(app)

    response = client.get("/evaluate")
    assert response.status_code == 401
