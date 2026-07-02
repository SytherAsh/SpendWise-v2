"""E4-S1-T1: /predict, /retrain, /evaluate must reject any request lacking a
matching X-Internal-Key header; /health stays exempt. Tested here against a
minimal standalone app (not api.main) so this story is verifiable before
E4-S2's real routes exist, per the epic's declared story order.
"""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from api.security import InternalKeyMiddleware

TEST_KEY = "test-internal-key"


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)

    @app.get("/predict")
    def predict() -> dict[str, str]:
        return {"status": "reached"}

    @app.get("/retrain")
    def retrain() -> dict[str, str]:
        return {"status": "reached"}

    @app.get("/evaluate")
    def evaluate() -> dict[str, str]:
        return {"status": "reached"}

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "healthy"}

    return TestClient(app)


@pytest.mark.parametrize("path", ["/predict", "/retrain", "/evaluate"])
def test_missing_header_returns_401(client: TestClient, path: str) -> None:
    response = client.get(path)
    assert response.status_code == 401


@pytest.mark.parametrize("path", ["/predict", "/retrain", "/evaluate"])
def test_wrong_key_returns_401(client: TestClient, path: str) -> None:
    response = client.get(path, headers={"X-Internal-Key": "wrong-key"})
    assert response.status_code == 401


@pytest.mark.parametrize("path", ["/predict", "/retrain", "/evaluate"])
def test_correct_key_passes_through(client: TestClient, path: str) -> None:
    response = client.get(path, headers={"X-Internal-Key": TEST_KEY})
    assert response.status_code == 200
    assert response.json() == {"status": "reached"}


def test_health_exempt_without_header(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
