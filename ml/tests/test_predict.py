"""E4-S2-T3: POST /predict — mocked model, verify response schema per
docs/testing.md §2 ("Prediction endpoint")."""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from api import model_store
from api.predict import router as predict_router
from api.security import InternalKeyMiddleware

TEST_KEY = "test-internal-key"


class FakeModel:
    """Stands in for the trained pipeline — predicts category_id 7 (Food / Dine
    Out) with high confidence, independent of the real committed artifact."""

    def predict(self, features):
        return [7]

    def predict_proba(self, features):
        return [[0.005] * 11 + [0.945]]


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)
    model_store.set_model(FakeModel())

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(predict_router)
    return TestClient(app)


VALID_PAYLOAD = {
    "recipient_name": "Swiggy",
    "upi_id": "swiggy@okicici",
    "bank": "ICICI",
    "transaction_mode": "UPI",
    "amount": -350.0,
    "note": None,
}


def test_predict_response_schema(client: TestClient) -> None:
    response = client.post("/predict", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"category_id", "category_name", "confidence"}
    assert body["category_id"] == 7
    assert body["category_name"] == "Food / Dine Out"
    assert isinstance(body["confidence"], float)
    assert 0.0 <= body["confidence"] <= 1.0


def test_predict_requires_internal_key(client: TestClient) -> None:
    response = client.post("/predict", json=VALID_PAYLOAD)
    assert response.status_code == 401


def test_predict_handles_null_optional_fields(client: TestClient) -> None:
    payload = {
        "recipient_name": None,
        "upi_id": None,
        "bank": None,
        "transaction_mode": None,
        "amount": 1500.0,
        "note": None,
    }
    response = client.post("/predict", json=payload, headers={"X-Internal-Key": TEST_KEY})
    assert response.status_code == 200
