"""evaluation/evaluate_recurring.py produces a report with all required
sections; GET /evaluate-recurring returns the same metrics as JSON.
"""

import pandas as pd
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from evaluation.evaluate_recurring import run_evaluation
from training.train_recurring import DEFAULT_DATA_PATH

TEST_KEY = "test-internal-key"

# Enough recurring and non-recurring candidate groups for an 80/20 stratified
# split without hitting the real ~1,800-row dataset.
_SYNTHETIC_ROWS = []
for i in range(10):
    base_date = pd.Timestamp("2023-01-01") + pd.Timedelta(days=i)
    # 3 occurrences within 56 days, identical amount -- comfortably satisfies
    # the strict rule (3+, within 60 days, within 10% of the minimum), so
    # bootstrap_label scores these 1.
    for day_offset in (0, 28, 56):
        _SYNTHETIC_ROWS.append(
            {
                "transaction_date": (base_date + pd.Timedelta(days=day_offset)).strftime("%Y-%m-%d"),
                "amount": -(500 + i),
                "recipient_name": f"RecurringMerchant{i}",
                "upi_id": None,
            }
        )
    # A one-off, non-recurring pair per merchant index: 2 occurrences ~200
    # days apart -- inside the loose 400-day window (so it still becomes a
    # candidate) but well outside the strict 60-day window (so bootstrap_label
    # scores it 0), giving both classes in the training frame.
    _SYNTHETIC_ROWS.append(
        {
            "transaction_date": "2023-01-05",
            "amount": -(2000 + i),
            "recipient_name": f"OneOff{i}",
            "upi_id": None,
        }
    )
    _SYNTHETIC_ROWS.append(
        {
            "transaction_date": "2023-07-24",
            "amount": -(2000 + i),
            "recipient_name": f"OneOff{i}",
            "upi_id": None,
        }
    )


@pytest.fixture
def synthetic_data_path(tmp_path):
    path = tmp_path / "synthetic.xlsx"
    pd.DataFrame(_SYNTHETIC_ROWS).to_excel(path, index=False)
    return path


def test_run_evaluation_produces_report_with_required_sections(synthetic_data_path, tmp_path) -> None:
    reports_dir = tmp_path / "reports"

    report = run_evaluation(data_path=synthetic_data_path, save_report=True, reports_dir=reports_dir)

    assert "accuracy" in report
    assert 0.0 <= report["accuracy"] <= 1.0
    assert "confusion_matrix" in report
    assert len(report["confusion_matrix"]) == 2
    assert len(report["confusion_matrix"][0]) == 2
    assert "confidence_distribution" in report
    assert {"mean", "median", "min", "max"} <= set(report["confidence_distribution"].keys())

    report_files = list(reports_dir.glob("evaluation_recurring_*.json"))
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

    assert report["n_candidate_groups"] > 50
    assert len(list(reports_dir.glob("evaluation_recurring_*.json"))) == 1


def test_evaluate_recurring_endpoint_returns_metrics_as_json(monkeypatch: pytest.MonkeyPatch) -> None:
    import api.evaluate_recurring as evaluate_recurring_module

    fake_report = {
        "generated_at": "2026-07-11T00:00:00+00:00",
        "n_candidate_groups": 50,
        "n_test_samples": 10,
        "accuracy": 0.9,
        "precision_recurring": 1.0,
        "recall_recurring": 0.8,
        "f1_recurring": 0.888,
        "support_recurring": 5,
        "support_not_recurring": 5,
        "confusion_matrix": [[5, 0], [1, 4]],
        "confusion_matrix_labels": [0, 1],
        "confidence_distribution": {"mean": 0.8, "median": 0.85, "min": 0.5, "max": 1.0},
        "report_path": "/tmp/fake.json",
    }
    monkeypatch.setattr(evaluate_recurring_module, "run_evaluation", lambda: fake_report)

    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)
    from api.security import InternalKeyMiddleware

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(evaluate_recurring_module.router)
    client = TestClient(app)

    response = client.get("/evaluate-recurring", headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body["accuracy"] == 0.9
    assert body["n_candidate_groups"] == 50


def test_evaluate_recurring_endpoint_requires_internal_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)
    import api.evaluate_recurring as evaluate_recurring_module
    from api.security import InternalKeyMiddleware

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(evaluate_recurring_module.router)
    client = TestClient(app)

    response = client.get("/evaluate-recurring")
    assert response.status_code == 401
