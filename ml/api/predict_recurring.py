"""POST /predict-recurring -- serves recurring-payment confidence/cadence
predictions. Internal-access-only, same as /predict (api/security.py's
InternalKeyMiddleware covers every route in this app, not just categorization's).

Per CLAUDE.md's "Categorization is the ML gateway" decision (docs/spec/
decisions.md ADR pending), the Alerts module never calls this directly --
it goes through the Spring Boot Categorization module's service interface,
which is the only module permitted to call this FastAPI service at all.
"""

import pandas as pd
from fastapi import APIRouter

from api import recurring_model_store
from api.schemas import RecurringPredictionRequest, RecurringPredictionResponse
from training.model_pipeline import RECURRING_FEATURE_COLUMNS
from training.recurring_features import derive_cadence

router = APIRouter()


@router.post("/predict-recurring", response_model=RecurringPredictionResponse)
def predict_recurring(request: RecurringPredictionRequest) -> RecurringPredictionResponse:
    model = recurring_model_store.get_model()
    features = pd.DataFrame([request.model_dump()], columns=RECURRING_FEATURE_COLUMNS)

    is_recurring = bool(model.predict(features)[0])
    confidence = float(max(model.predict_proba(features)[0]))
    cadence = derive_cadence(request.interval_mean_days) if is_recurring else "irregular"

    return RecurringPredictionResponse(is_recurring=is_recurring, confidence=confidence, cadence=cadence)
