"""POST /normalize-recipients — real clustering pipeline, verify response
schema and internal-key enforcement."""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from api.normalize_recipients import router as normalize_recipients_router
from api.security import InternalKeyMiddleware

TEST_KEY = "test-internal-key"


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("ML_INTERNAL_KEY", TEST_KEY)

    app = FastAPI()
    app.add_middleware(InternalKeyMiddleware)
    app.include_router(normalize_recipients_router)
    return TestClient(app)


VALID_PAYLOAD = {
    "entries": [
        {"key": "t1", "recipient_name": "Swiggy", "upi_id": "swiggy@okicici"},
        {"key": "t2", "recipient_name": "SWIGGY BANGALORE", "upi_id": "swiggy@okicici"},
        {"key": "t3", "recipient_name": "Uber", "upi_id": None},
    ]
}


def test_normalize_recipients_response_schema(client: TestClient) -> None:
    response = client.post("/normalize-recipients", json=VALID_PAYLOAD, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    body = response.json()
    assert set(body.keys()) == {"canonical_names", "ambiguous_groups"}
    assert set(body["canonical_names"].keys()) == {"t1", "t2", "t3"}
    assert body["canonical_names"]["t1"] == body["canonical_names"]["t2"]
    assert body["canonical_names"]["t1"] != body["canonical_names"]["t3"]
    assert body["ambiguous_groups"] == []


def test_normalize_recipients_requires_internal_key(client: TestClient) -> None:
    response = client.post("/normalize-recipients", json=VALID_PAYLOAD)
    assert response.status_code == 401


def test_normalize_recipients_handles_empty_entries(client: TestClient) -> None:
    response = client.post("/normalize-recipients", json={"entries": []}, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    assert response.json() == {"canonical_names": {}, "ambiguous_groups": []}


def test_normalize_recipients_surfaces_ambiguous_groups(client: TestClient) -> None:
    """A prefix-ambiguous case (real data pattern: a bare first name that could
    belong to either of two fuller, unrelated-looking names) shows up in
    ambiguous_groups rather than being silently left unmerged with no trace."""
    payload = {
        "entries": [
            {"key": "a1", "recipient_name": "Sameer", "upi_id": None},
            {"key": "a2", "recipient_name": "SAMEER SAWANT", "upi_id": None},
            {"key": "a3", "recipient_name": "SAMEER BALIRAM SAWA", "upi_id": None},
        ]
    }

    response = client.post("/normalize-recipients", json=payload, headers={"X-Internal-Key": TEST_KEY})

    assert response.status_code == 200
    groups = response.json()["ambiguous_groups"]
    assert len(groups) == 2
    anchors = {g["anchor_key"] for g in groups}
    assert anchors == {"a2", "a3"}
    for group in groups:
        assert [c["key"] for c in group["candidates"]] == ["a1"]
        assert group["candidates"][0]["reason"] == "prefix_ambiguous"
