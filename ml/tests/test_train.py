"""E4-S2-T2: the committed model artifact must load and predict a valid
category_id for a known-good sample input."""

from pathlib import Path

import joblib
import pytest

from api.categories import CATEGORIES
from training.preprocessing import build_feature_frame

MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "category_classifier.joblib"


@pytest.fixture(scope="module")
def model():
    if not MODEL_PATH.exists():
        pytest.skip(f"Model artifact not found at {MODEL_PATH} — run training/train.py first.")
    return joblib.load(MODEL_PATH)


def test_model_predicts_a_valid_category_id(model) -> None:
    sample = {
        "recipient_name": "Swiggy",
        "upi_id": "swiggy@okicici",
        "bank": "ICICI",
        "transaction_mode": "UPI",
        "amount": -350.0,
        "note": None,
    }
    features = build_feature_frame([sample])

    prediction = model.predict(features)[0]

    assert 1 <= int(prediction) <= len(CATEGORIES)


def test_model_predict_proba_gives_valid_confidence(model) -> None:
    sample = {
        "recipient_name": "PRACHI S",
        "upi_id": "prachi24",
        "bank": "SBIN",
        "transaction_mode": "UPI",
        "amount": -10.0,
        "note": "UPI",
    }
    features = build_feature_frame([sample])

    probabilities = model.predict_proba(features)[0]

    assert 0.0 <= max(probabilities) <= 1.0
    assert abs(sum(probabilities) - 1.0) < 1e-6
