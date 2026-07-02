"""POST /predict (E4-S2-T3) — serves category predictions for the Categorization
module. Response schema per docs/deployment.md "Backend Service Communication".
"""

from fastapi import APIRouter

from api import model_store
from api.categories import category_name_for_id
from api.schemas import PredictionRequest, PredictionResponse
from training.preprocessing import build_feature_frame

router = APIRouter()


@router.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest) -> PredictionResponse:
    model = model_store.get_model()
    features = build_feature_frame([request.model_dump()])

    category_id = int(model.predict(features)[0])
    confidence = float(max(model.predict_proba(features)[0]))

    return PredictionResponse(
        category_id=category_id,
        category_name=category_name_for_id(category_id),
        confidence=confidence,
    )
