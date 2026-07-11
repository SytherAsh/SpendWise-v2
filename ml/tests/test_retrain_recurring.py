"""POST /retrain-recurring — mocked training call at unit level; the slow
end-to-end variant exercises the real bootstrap pipeline against the real
labeled dataset when present."""

import pandas as pd
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

import api.retrain_recurring as retrain_recurring_module
from api.security import InternalKeyMiddleware
from training.train_recurring import DEFAULT_DATA_PATH

TEST_KEY = "test-internal-key"

VALID_PAYLOAD = {
    "corrections": [
        {
            "occurrence_count": 4,
            "interval_mean_days": 30.0,
            "interval_cv": 0.05,
            "amount_mean": 799.0,
            "amount_cv": 0.02,
            "span_days": 91.0,
            "days_since_last_occurrence": 3.0,
            "was_recurring": True,
        }
    ]
}


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(retrain_recurring_module.router)
    return TestClient(app)


def test_retrain_recurring_combines_bootstrap_and_corrections(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict = {}

    def fake_load_labeled_dataset(path):
        return pd.DataFrame(
            [
                {"transaction_date": "2023-01-01", "amount": -800.0, "recipient_name": "Gym", "upi_id": None},
                {"transaction_date": "2023-01-31", "amount": -800.0, "recipient_name": "Gym", "upi_id": None},
                {"transaction_date": "2023-03-01", "amount": -800.0, "recipient_name": "Gym", "upi_id": None},
            ]
        )

    def fake_build_pipeline():
        class FakeModel:
            def fit(self, x, y):
                captured["n_rows"] = len(x)
                captured["has_correction_row"] = bool((x["interval_mean_days"] == 30.0).any())

        return FakeModel()

    def fake_save_model(model, output_dir):
        captured["saved"] = True
        return output_dir / "recurring_classifier.joblib"

    monkeypatch.setattr(retrain_recurring_module, "load_labeled_dataset", fake_load_labeled_dataset)
    monkeypatch.setattr(retrain_recurring_module, "build_recurring_pipeline", fake_build_pipeline)
    monkeypatch.setattr(retrain_recurring_module, "save_model", fake_save_model)
    monkeypatch.setattr(retrain_recurring_module.recurring_model_store, "set_model", lambda model: captured.setdefault("model_set", True))

    response = client.post("/retrain-recurring", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "success"
    assert captured["has_correction_row"] is True
    assert captured["saved"] is True
    assert captured["model_set"] is True


def test_retrain_recurring_requires_internal_key(client: TestClient) -> None:
    response = client.post("/retrain-recurring", json=VALID_PAYLOAD)
    assert response.status_code == 401


@pytest.mark.slow
@pytest.mark.skipif(
    not DEFAULT_DATA_PATH.exists(),
    reason="ml/data/spendwise_labeled.xlsx is gitignored (real personal data) — absent in CI checkouts.",
)
def test_retrain_recurring_end_to_end_real_training(client: TestClient, monkeypatch: pytest.MonkeyPatch, tmp_path) -> None:
    monkeypatch.setenv("MODEL_PATH", str(tmp_path))

    response = client.post("/retrain-recurring", json={"corrections": []}, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "success"
    assert body["trained_candidate_groups"] > 100

    artifact = tmp_path / "recurring_classifier.joblib"
    assert artifact.exists()

    import joblib

    reloaded = joblib.load(artifact)
    assert reloaded.predict is not None
