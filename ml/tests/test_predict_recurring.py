"""POST /predict-recurring — mocked model, verify response schema."""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from api import recurring_model_store
from api.predict_recurring import router as predict_recurring_router
from api.security import InternalKeyMiddleware

TEST_KEY = "test-internal-key"


class FakeRecurringModel:
    """Predicts "recurring" with high confidence, independent of the real
    committed artifact."""

    def predict(self, features):
        return [1]

    def predict_proba(self, features):
        return [[0.05, 0.95]]


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)
    recurring_model_store.set_model(FakeRecurringModel())

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(predict_recurring_router)
    return TestClient(app)


VALID_PAYLOAD = {
    "occurrence_count": 4,
    "interval_mean_days": 30.0,
    "interval_cv": 0.05,
    "amount_mean": 799.0,
    "amount_cv": 0.02,
    "span_days": 91.0,
    "days_since_last_occurrence": 3.0,
}


def test_predict_recurring_response_schema(client: TestClient) -> None:
    response = client.post("/predict-recurring", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"is_recurring", "confidence", "cadence"}
    assert body["is_recurring"] is True
    assert body["cadence"] == "monthly"
    assert 0.0 <= body["confidence"] <= 1.0


def test_predict_recurring_requires_internal_key(client: TestClient) -> None:
    response = client.post("/predict-recurring", json=VALID_PAYLOAD)
    assert response.status_code == 401


def test_predict_recurring_reports_irregular_cadence_when_not_recurring(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    class NotRecurringModel:
        def predict(self, features):
            return [0]

        def predict_proba(self, features):
            return [[0.9, 0.1]]

    recurring_model_store.set_model(NotRecurringModel())
    response = client.post("/predict-recurring", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert body["is_recurring"] is False
    assert body["cadence"] == "irregular"
