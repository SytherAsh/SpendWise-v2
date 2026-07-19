"""E4-S2-T4: POST /retrain — loads corrections + baseline data and triggers
training without error. The fast test mocks train_model per docs/testing.md
§2 ("mocked training call is acceptable at unit level"); test_retrain_end_to_end
is the slower, real-training variant explicitly allowed to be marked slow.
"""

import pandas as pd
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

import api.retrain as retrain_module
from api.security import InternalKeyMiddleware
from training.dataset_locator import find_latest_dataset_file, NoLabeledDatasetFoundError

TEST_KEY = "test-internal-key"

VALID_PAYLOAD = {
    "corrections": [
        {
            "recipient_name": "Test Gym",
            "upi_id": "testgym@okhdfc",
            "bank": "HDFC",
            "transaction_mode": "UPI",
            "amount": -800.0,
            "note": None,
            "category_id": 3,
        }
    ]
}


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(retrain_module.router)
    return TestClient(app)


def test_retrain_loads_corrections_and_triggers_training(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict = {}

    def fake_load_labeled_dataset(path):
        return pd.DataFrame(
            [
                {"recipient_name": "Swiggy", "amount": -300.0, "category": "Food / Dine Out"},
                {"recipient_name": "DMart", "amount": -900.0, "category": "Groceries"},
            ]
        )

    def fake_train_model(df):
        captured["n_rows"] = len(df)
        captured["has_gym_row"] = bool((df["recipient_name"] == "Test Gym").any())
        return object(), len(df)

    def fake_save_model(pipeline, output_dir):
        captured["saved"] = True
        return output_dir / "category_classifier.joblib"

    monkeypatch.setattr(retrain_module, "load_labeled_dataset", fake_load_labeled_dataset)
    monkeypatch.setattr(retrain_module, "train_model", fake_train_model)
    monkeypatch.setattr(retrain_module, "save_model", fake_save_model)
    monkeypatch.setattr(retrain_module.model_store, "set_model", lambda pipeline: captured.setdefault("model_set", True))

    response = client.post("/retrain", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body == {"status": "success", "trained_samples": 3}
    assert captured["n_rows"] == 3
    assert captured["has_gym_row"] is True
    assert captured["saved"] is True
    assert captured["model_set"] is True


def test_retrain_requires_internal_key(client: TestClient) -> None:
    response = client.post("/retrain", json=VALID_PAYLOAD)
    assert response.status_code == 401


def test_retrain_with_no_corrections_uses_baseline_only(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_load_labeled_dataset(path):
        return pd.DataFrame([{"recipient_name": "Swiggy", "amount": -300.0, "category": "Food / Dine Out"}])

    captured: dict = {}

    def fake_train_model(df):
        captured["n_rows"] = len(df)
        return object(), len(df)

    monkeypatch.setattr(retrain_module, "load_labeled_dataset", fake_load_labeled_dataset)
    monkeypatch.setattr(retrain_module, "train_model", fake_train_model)
    monkeypatch.setattr(retrain_module, "save_model", lambda pipeline, output_dir: output_dir / "m.joblib")
    monkeypatch.setattr(retrain_module.model_store, "set_model", lambda pipeline: None)

    response = client.post("/retrain", json={"corrections": []}, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    assert captured["n_rows"] == 1


def _no_real_dataset() -> bool:
    try:
        find_latest_dataset_file()
        return False
    except NoLabeledDatasetFoundError:
        return True


@pytest.mark.slow
@pytest.mark.skipif(
    _no_real_dataset(),
    reason="ml/data/ has no labeled dataset (gitignored, real personal data) — absent in CI checkouts.",
)
def test_retrain_end_to_end_real_training(client: TestClient, monkeypatch: pytest.MonkeyPatch, tmp_path) -> None:
    """Real (unmocked) retrain against the actual baseline dataset, writing the
    new artifact to a scratch directory so the committed models/ artifact is
    never touched by running tests."""
    monkeypatch.setenv("MODEL_PATH", str(tmp_path))

    response = client.post("/retrain", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "success"
    assert body["trained_samples"] > 1800

    artifact = tmp_path / "category_classifier.joblib"
    assert artifact.exists()

    import joblib

    reloaded = joblib.load(artifact)
    assert reloaded.predict is not None
